package com.revents.eventstore;

import static com.revents.log.TransformContextToMdc.fluxLogOnNext;
import static com.revents.log.TransformContextToMdc.monoLogOnNext;
import static com.revents.log.TransformContextToMdc.monoLogOnSubscribe;

import com.google.common.collect.Iterables;
import com.revents.AggregateId;
import com.revents.EventMessage;
import com.revents.EventMessage.EventId;
import com.revents.EventMessage.EventMetaData;
import com.revents.EventStore;
import com.revents.EventStreamSubscription;
import com.revents.ReventsException;
import com.revents.RevisionMismatchException;
import com.revents.StreamName;
import com.revents.StreamReadRequest;
import com.revents.cqrs.StreamNameResolver;
import com.revents.serialization.SerializedTypeMapper;
import com.revents.serialization.Serializer;
import io.r2dbc.spi.Row;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@SuppressWarnings({"PMD.ExcessiveImports", "PMD.TooManyMethods"})
public class SpringR2dbcEventStore implements EventStore {

    private static final Logger LOG = LoggerFactory.getLogger(SpringR2dbcEventStore.class);

    private static final String EVENT_INSERT = "INSERT INTO event "
        + "(event_id, aggregate_type, aggregate_id, revision, created, event_type, data) "
        + "VALUES ($1, $2, $3, $4, $5, $6, $7)";
    private static final String AGGREGATE_SELECT = "SELECT revision FROM aggregate "
        + "WHERE aggregate_type=$1 AND aggregate_id=$2 LIMIT 1";
    private static final String AGGREGATE_CREATE = "INSERT INTO aggregate (aggregate_type, aggregate_id, revision) "
        + "VALUES ($1, $2, $3)";
    private static final String AGGREGATE_UPDATE = "UPDATE aggregate SET revision=$1 "
        + "WHERE aggregate_type=$2 AND aggregate_id=$3 AND revision=$4";
    private static final String EVENT_SEQUENCE_NUMBER = "SELECT sequence_number FROM event WHERE event_id=$1 LIMIT 1";
    private static final String AGGREGATE_EVENTS = "SELECT * FROM event "
        + "WHERE aggregate_type=$1 AND aggregate_id=$2 AND revision>$3 ORDER BY sequence_number";
    private static final String NEXT_ALL_EVENT_BUCKET = "SELECT * FROM event "
        + "WHERE sequence_number>$1 ORDER BY sequence_number LIMIT $2";
    private static final String NEXT_STREAM_EVENT_BUCKET = "SELECT event.* FROM event "
        + "INNER JOIN stream_link ON event.event_id=stream_link.event_id "
        + "WHERE stream_name=$1 AND sequence_number>$2 ORDER BY sequence_number LIMIT $3";
    private static final String STREAM_LINK_INSERT = "INSERT INTO stream_link (event_id, stream_name) VALUES ($1, $2)";

    private final DatabaseClient databaseClient;
    private final ReactiveTransactionManager reactiveTransactionManager;
    private final Serializer serializer;
    private final SerializedTypeMapper serializedTypeMapper;
    private final int eventStreamBucketSize;
    private final Duration eventStreamFetchRepeat;
    private final TokenStore tokenStore;
    private final StreamNameResolver streamNameResolver;

    public static SpringR2dbcEventStore create(UnaryOperator<SpringR2dbcEventStoreConfig.Builder> init) {
        return new SpringR2dbcEventStore(init.apply(new SpringR2dbcEventStoreConfig.Builder()).build());
    }

    private SpringR2dbcEventStore(SpringR2dbcEventStoreConfig config) {
        databaseClient = config.databaseClient();
        reactiveTransactionManager = config.reactiveTransactionManager();
        serializer = config.serializer();
        serializedTypeMapper = config.serializedTypeMapper();
        eventStreamBucketSize = config.eventStreamBucketSize();
        eventStreamFetchRepeat = config.eventStreamFetchRepeat();
        tokenStore = config.tokenStore();
        streamNameResolver = config.streamNameResolver();
    }

    @Override
    public Mono<List<EventMessage<?>>> store(AggregateId<?> aggregateId,
                                             List<EventMessage<?>> eventMessages,
                                             OptionalLong originatingRevision) {

        return aggregateRevision(databaseClient, aggregateId)
            .flatMap(currentRevision -> Flux.fromIterable(eventMessages)
                .zipWith(Flux.fromStream(Stream.iterate(currentRevision + 1, i -> i + 1)))
                .concatMap(tuple -> save(databaseClient, tuple.getT1(), tuple.getT2()))
                .flatMap(eventMessage -> streamNameResolver.streamsOf(eventMessage.payload())
                    .flatMap(streamName -> saveStreamLink(databaseClient, eventMessage, streamName))
                    .then(Mono.<EventMessage<?>>just(eventMessage)))
                .collectList()
                .flatMap(persistedEventMessages -> updateAggregate(
                    databaseClient,
                    aggregateId,
                    originatingRevision.orElse(currentRevision),
                    Iterables.getLast(persistedEventMessages).metaData().revision().getAsLong())
                    .thenReturn(persistedEventMessages)))
            .as(TransactionalOperator.create(reactiveTransactionManager)::transactional)
            .transform(monoLogOnNext(e -> LOG.debug("Events have been persisted={}", e)))
            .subscribeOn(Schedulers.boundedElastic())
            .publishOn(Schedulers.parallel());
    }

    @Override
    public EventStreamSubscription readEventStream(StreamReadRequest streamReadRequest) {
        Flux<EventMessage<?>> events = Mono.justOrEmpty(streamReadRequest.eventProcessorId())
            .transform(monoLogOnSubscribe(x ->
                LOG.debug("Subscribed to event stream with request={}", streamReadRequest)))
            .flatMap(tokenStore::tokenFor)
            .switchIfEmpty(Mono.just(streamReadRequest.startReadingFrom()))
            .flatMap(position -> Mono.justOrEmpty(position.lastProcessedEvent()))
            .flatMap(this::sequenceNumber)
            .switchIfEmpty(Mono.just(0L))
            .flatMapMany(sequenceNumber -> readEventMessages(streamReadRequest.streamName(), sequenceNumber))
            .transform(fluxLogOnNext(e -> LOG.debug("Event emitted from stream {}", e)))
            .transform(eventMessageFlux -> streamReadRequest.infinite()
                ? eventMessageFlux
                .repeatWhen(emittedEachAttempt -> Flux.interval(eventStreamFetchRepeat, Schedulers.boundedElastic())
                    .onBackpressureDrop())
                : eventMessageFlux)
            .subscribeOn(Schedulers.boundedElastic())
            .publishOn(Schedulers.parallel());

        return TokenStoredEventStreamSubscription.builder()
            .events(events)
            .tokenStore(tokenStore)
            .eventProcessorId(streamReadRequest.eventProcessorId())
            .build();
    }

    @Override
    public Flux<EventMessage<?>> eventsForAggregate(AggregateId<?> aggregateId, OptionalLong afterRevision) {
        return databaseClient.sql(AGGREGATE_EVENTS)
            .bind("$1", serializedTypeMapper.classToStringEagerly(aggregateId.aggregateRootType()))
            .bind("$2", aggregateId.aggregateRootId())
            .bind("$3", afterRevision.orElse(0L))
            .map(toEventMessage())
            .all()
            .subscribeOn(Schedulers.boundedElastic())
            .publishOn(Schedulers.parallel());
    }

    private Flux<EventMessage<?>> readEventMessages(StreamName streamName, Long sequenceNumber) {
        DatabaseClient.GenericExecuteSpec fetch;
        if (streamName.equals(StreamName.ALL)) {
            fetch = databaseClient.sql(NEXT_ALL_EVENT_BUCKET)
                .bind("$1", sequenceNumber)
                .bind("$2", eventStreamBucketSize);
        } else {
            fetch = databaseClient.sql(NEXT_STREAM_EVENT_BUCKET)
                .bind("$1", streamName.value())
                .bind("$2", sequenceNumber)
                .bind("$3", eventStreamBucketSize);
        }
        return fetch
            .map(toEventMessage())
            .all();
    }

    private Mono<Long> sequenceNumber(EventId eventId) {
        return Mono.just(eventId.rawId())
            .flatMap(rawId -> databaseClient.sql(EVENT_SEQUENCE_NUMBER)
                .bind("$1", rawId)
                .map(row -> row.get("sequence_number", Long.class))
                .one())
            .switchIfEmpty(Mono.just(0L));
    }

    private Function<Row, EventMessage<?>> toEventMessage() {
        return row -> {
            try {
                return EventMessage.builder()
                    .payload(serializer.deserialize(
                        row.get("data", String.class),
                        Class.forName(row.get("event_type", String.class))))
                    .metaData(EventMetaData.builder()
                        .id(EventId.of(row.get("event_id", String.class)))
                        .created(row.get("created", OffsetDateTime.class))
                        .revision(row.get("revision", Long.class))
                        .aggregateId(AggregateId.of(
                            serializedTypeMapper.stringToClassEagerly(row.get("aggregate_type", String.class)),
                            row.get("aggregate_id", String.class)))
                        .build())
                    .build();
            } catch (ClassNotFoundException e) {
                throw new ReventsException("Event cannot be deserialized as class cannot be found", e);
            }
        };
    }

    private <T> Mono<Void> updateAggregate(DatabaseClient client,
                                           AggregateId<T> aggregateId,
                                           long expectedRevision,
                                           long newRevision) {

        return client.sql(AGGREGATE_UPDATE)
            .bind("$1", newRevision)
            .bind("$2", serializedTypeMapper.classToStringEagerly(aggregateId.aggregateRootType()))
            .bind("$3", aggregateId.aggregateRootId())
            .bind("$4", expectedRevision)
            .fetch()
            .rowsUpdated()
            .filter(updatedRows -> updatedRows.equals(1))
            .switchIfEmpty(Mono.error(() -> new RevisionMismatchException(aggregateId, expectedRevision)))
            .then();
    }

    private Mono<Void> saveStreamLink(DatabaseClient client, EventMessage<?> eventMessage, StreamName streamName) {
        return client.sql(STREAM_LINK_INSERT)
            .bind("$1", eventMessage.metaData().id().rawId())
            .bind("$2", streamName.value())
            .fetch()
            .rowsUpdated()
            .then();
    }

    private Mono<EventMessage<?>> save(DatabaseClient client, EventMessage<?> eventMessage, long currentRevision) {
        return client.sql(EVENT_INSERT)
            .bind("$1", eventMessage.metaData().id().rawId())
            .bind("$2", serializedTypeMapper.classToStringEagerly(
                eventMessage.metaData().aggregateId().aggregateRootType()))
            .bind("$3", eventMessage.metaData().aggregateId().aggregateRootId())
            .bind("$4", eventMessage.metaData().revision().orElse(currentRevision))
            .bind("$5", eventMessage.metaData().created())
            .bind("$6", eventMessage.payload().getClass().getName())
            .bind("$7", serializer.serialize(eventMessage.payload()))
            .fetch()
            .rowsUpdated()
            .map(x -> eventMessage.toPersisted(eventMessage.metaData().revision().orElse(currentRevision)));
    }

    private <T> Mono<Long> aggregateRevision(DatabaseClient client, AggregateId<T> aggregateId) {
        return client.sql(AGGREGATE_SELECT)
            .bind("$1", serializedTypeMapper.classToStringEagerly(aggregateId.aggregateRootType()))
            .bind("$2", aggregateId.aggregateRootId())
            .map(row -> row.get("revision", Long.class))
            .one()
            .switchIfEmpty(client.sql(AGGREGATE_CREATE)
                .bind("$1", serializedTypeMapper.classToStringEagerly(aggregateId.aggregateRootType()))
                .bind("$2", aggregateId.aggregateRootId())
                .bind("$3", 0L)
                .fetch()
                .rowsUpdated()
                .thenReturn(0L));
    }

    @Value.Immutable
    public interface SpringR2dbcEventStoreConfig {

        DatabaseClient databaseClient();

        ReactiveTransactionManager reactiveTransactionManager();

        TokenStore tokenStore();

        /**
         * Create a default Jackson serializer if no {@link Serializer} was provided.
         *
         * @return a Jackson serializer
         */
        @Value.Default
        default Serializer serializer() {
            return Serializer.defaultJackson();
        }

        @Value.Default
        default SerializedTypeMapper serializedTypeMapper() {
            return SerializedTypeMapper.CLASS_NAME_BASED;
        }

        @Value.Default
        default int eventStreamBucketSize() {
            return 100;
        }

        @Value.Default
        default Duration eventStreamFetchRepeat() {
            return Duration.ofSeconds(5);
        }

        @Value.Default
        default StreamNameResolver streamNameResolver() {
            return StreamNameResolver.ANNOTATION_BASED;
        }

        class Builder extends ImmutableSpringR2dbcEventStoreConfig.Builder {
        }
    }
}
