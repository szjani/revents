package com.revents.eventstore;

import static com.revents.log.TransformContextToMdc.fluxLogOnNext;
import static com.revents.log.TransformContextToMdc.monoLogOnNext;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.revents.AggregateId;
import com.revents.EventMessage;
import com.revents.EventMessage.EventId;
import com.revents.EventStore;
import com.revents.EventStreamSubscription;
import com.revents.RevisionMismatchException;
import com.revents.StreamName;
import com.revents.StreamReadRequest;
import com.revents.cqrs.StreamNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@SuppressWarnings("PMD.ExcessiveImports")
public class InMemoryEventStore implements EventStore {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryEventStore.class);

    private final NavigableMap<EventMessageKey, EventMessage<?>> events = new TreeMap<>();
    private final Map<EventId, EventMessageKey> sequenceMap = new ConcurrentHashMap<>();
    private final LinkedListMultimap<AggregateId<?>, EventMessage<?>> aggregateEventMap = LinkedListMultimap.create();
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    private final TokenStore tokenStore;
    private final StreamNameResolver streamNameResolver;

    public InMemoryEventStore(TokenStore tokenStore, StreamNameResolver streamNameResolver) {
        this.tokenStore = requireNonNull(tokenStore);
        this.streamNameResolver = requireNonNull(streamNameResolver);
    }

    public InMemoryEventStore(TokenStore tokenStore) {
        this(tokenStore, StreamNameResolver.ANNOTATION_BASED);
    }

    @Override
    public Mono<List<EventMessage<?>>> store(AggregateId<?> aggregateId,
                                             List<EventMessage<?>> eventMessages,
                                             OptionalLong originatingRevision) {
        return Mono.fromCallable(() -> persistEvents(aggregateId, eventMessages, originatingRevision))
            .transform(monoLogOnNext(e -> LOG.debug("Events have been persisted={}", e)))
            .subscribeOn(Schedulers.parallel());
    }

    private List<EventMessage<?>> persistEvents(AggregateId<?> aggregateId,
                                                List<EventMessage<?>> eventMessages,
                                                OptionalLong originatingRevision) {
        synchronized (events) {
            long currentRevision = Optional.ofNullable(Iterables.getLast(
                    aggregateEventMap.get(aggregateId), null))
                .map(lastMessage -> lastMessage.metaData().revision())
                .map(OptionalLong::getAsLong)
                .orElse(0L);

            originatingRevision
                .ifPresent(expected -> {
                    if (currentRevision != expected) {
                        throw new RevisionMismatchException(aggregateId, expected);
                    }
                });

            AtomicLong versioner = new AtomicLong(currentRevision);
            return eventMessages.stream()
                .map(eventMessage -> eventMessage.toPersisted(versioner.incrementAndGet()))
                .peek(eventMessage -> {
                    EventMessageKey eventMessageKey = new EventMessageKey(
                        sequenceGenerator.incrementAndGet(), eventMessage.metaData().id());
                    sequenceMap.put(eventMessage.metaData().id(), eventMessageKey);
                    events.put(eventMessageKey, eventMessage);
                    aggregateEventMap.put(aggregateId, eventMessage);
                })
                .collect(Collectors.toList());
        }
    }

    @Override
    public EventStreamSubscription readEventStream(StreamReadRequest streamReadRequest) {
        Flux<EventMessage<?>> events = Mono.justOrEmpty(streamReadRequest.eventProcessorId())
            .flatMap(tokenStore::tokenFor)
            .switchIfEmpty(Mono.just(streamReadRequest.startReadingFrom()))
            .flatMap(position -> Mono.justOrEmpty(position.lastProcessedEvent()))
            .map(sequenceMap::get)
            .map(eventMessageKey -> this.events.tailMap(eventMessageKey, false).values())
            .switchIfEmpty(Mono.defer(() -> Mono.just(this.events.values())))
            .flatMapMany(Flux::fromIterable)
            .filterWhen(eventMessage -> Flux.just(StreamName.ALL)
                .concatWith(streamNameResolver.streamsOf(eventMessage.payload()))
                .any(containedStream -> streamReadRequest.streamName().equals(containedStream)))
            .transform(fluxLogOnNext(e -> LOG.debug("Event emitted from stream {}", e)))
            .transform(eventMessageFlux -> streamReadRequest.infinite()
                ? eventMessageFlux
                    .repeatWhen(emittedEachAttempt -> Flux.interval(Duration.ofMillis(100), Schedulers.boundedElastic())
                        .onBackpressureDrop())
                : eventMessageFlux);

        return TokenStoredEventStreamSubscription.builder()
            .events(events)
            .tokenStore(tokenStore)
            .eventProcessorId(streamReadRequest.eventProcessorId())
            .build();
    }

    @Override
    public Flux<EventMessage<?>> eventsForAggregate(AggregateId<?> aggregateId, OptionalLong afterRevision) {
        return Flux.fromIterable(aggregateEventMap.get(aggregateId))
            .skipUntil(eventMessage -> afterRevision.isEmpty()
                || afterRevision.getAsLong() < eventMessage.metaData().revision().getAsLong());
    }

    /**
     * Reset the in-memory store. Use only for testing purposes.
     */
    public void reset() {
        events.clear();
        sequenceMap.clear();
        aggregateEventMap.clear();
        sequenceGenerator.set(0);
    }

    private static final class EventMessageKey implements Comparable<EventMessageKey> {

        private final long sequenceNumber;
        private final EventId eventId;

        private EventMessageKey(long sequenceNumber, EventId eventId) {
            this.sequenceNumber = sequenceNumber;
            this.eventId = eventId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            EventMessageKey that = (EventMessageKey) o;
            return eventId.equals(that.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId);
        }

        @Override
        public int compareTo(EventMessageKey o) {
            return Long.compare(sequenceNumber, o.sequenceNumber);
        }
    }
}
