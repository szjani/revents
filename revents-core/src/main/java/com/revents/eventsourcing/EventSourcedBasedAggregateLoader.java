package com.revents.eventsourcing;

import com.revents.AggregateId;
import com.revents.AggregateLoader;
import com.revents.EventHandler;
import com.revents.EventMessage;
import com.revents.EventStore;
import com.revents.NotFollowedReventsConventionException;
import com.revents.ReventsClock;
import com.revents.eventsourcing.SnapshotRepository.Snapshot;
import org.immutables.value.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.UnaryOperator;

public class EventSourcedBasedAggregateLoader implements AggregateLoader {

    private final EventStore eventStore;
    private final SnapshotStrategyFactory snapshotStrategyFactory;
    private final SnapshotRepository snapshotRepository;

    private EventSourcedBasedAggregateLoader(EventSourcedBasedAggregateLoaderConfig config) {
        eventStore = config.eventStore();
        snapshotStrategyFactory = config.snapshotStrategyFactory();
        snapshotRepository = config.snapshotRepository().orElse(null);
    }

    /**
     * Create a new {@link EventStore} based {@link AggregateLoader}.
     *
     * @param init the config initializer
     * @return a new aggregate loader
     */
    public static EventSourcedBasedAggregateLoader create(
        UnaryOperator<EventSourcedBasedAggregateLoaderConfig.Builder> init) {

        return new EventSourcedBasedAggregateLoader(init.apply(
            new EventSourcedBasedAggregateLoaderConfig.Builder()).build());
    }

    @Override
    public <T> Mono<T> load(AggregateId<T> aggregateId) {
        return latestSnapshotFor(aggregateId)
            .switchIfEmpty(instantiate(aggregateId.aggregateRootType()))
            .flatMap(pair -> eventStore.eventsForAggregate(aggregateId, pair.revision())
                .concatMap(event -> replayEvent(pair.aggregate(), event))
                .transform(events -> createSnapshots(aggregateId, pair.aggregate(), events))
                .then(Mono.just(pair.aggregate())));
    }

    private <T> Flux<Void> createSnapshots(AggregateId<T> aggregateId, T aggregate, Flux<EventMessage<?>> events) {
        return Mono.deferContextual(contextView -> Mono.just(contextView.get(Clock.class)))
            .flatMapMany(clock -> snapshotStrategyFactory.createFor(aggregateId).needSnapshot(events, null)
                .concatMap(tuple -> Mono.just(tuple)
                    .map(Tuple2::getT2)
                    .filter(Boolean::booleanValue)
                    .flatMap(x -> snapshotRepository.save(Snapshot.<T>builder()
                        .aggregateId(aggregateId)
                        .aggregate(aggregate)
                        .revision(tuple.getT1().metaData().revision().orElseThrow(() ->
                            new IllegalStateException("Revision is missing from a persisted event " + tuple.getT1())))
                        .basedOnEvent(tuple.getT1().metaData().id())
                        .created(OffsetDateTime.now(clock))
                        .build())
                        .subscribeOn(Schedulers.parallel()))))
            .as(ReventsClock.fluxContextualClock());
    }

    private <T> Mono<EventMessage<?>> replayEvent(T aggregate, EventMessage<?> event) {
        return handlerMethodFor(event)
            .flatMap(eventHandler -> Mono.fromRunnable(() -> {
                try {
                    eventHandler.setAccessible(true);
                    eventHandler.invoke(aggregate, event.payload());
                } catch (ReflectiveOperationException e) {
                    throw new NotFollowedReventsConventionException("Could not call event handler " + eventHandler, e);
                }
            }))
            .thenReturn(event);
    }

    private <T> Mono<AggregateLowerBoundPair<T>> latestSnapshotFor(AggregateId<T> aggregateId) {
        return Mono.justOrEmpty(snapshotRepository)
            .flatMap(repo -> repo.load(aggregateId))
            .map(snapshot -> AggregateLowerBoundPair.of(snapshot.aggregate(), OptionalLong.of(snapshot.revision())));
    }

    private <T> Mono<AggregateLowerBoundPair<T>> instantiate(Class<T> aggregateRootType) {
        return Mono.fromCallable(() -> AggregateLowerBoundPair.of(
            newAggregateInstance(aggregateRootType), OptionalLong.empty()));
    }

    private Mono<Method> handlerMethodFor(EventMessage<?> event) {
        return Flux.fromArray(event.metaData().aggregateId().aggregateRootType().getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(EventHandler.class))
            .filter(method -> method.getParameterCount() == 1)
            .filter(method -> method.getParameterTypes()[0].isAssignableFrom(event.payload().getClass()))
            .switchIfEmpty(Mono.error(() ->
                new NotFollowedReventsConventionException("No event sourced handler found for event " + event)))
            .last();
    }

    private <T> T newAggregateInstance(Class<T> aggregateRootType) {
        try {
            Constructor<T> declaredConstructor = aggregateRootType.getDeclaredConstructor();
            declaredConstructor.setAccessible(true);
            return declaredConstructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new NotFollowedReventsConventionException(
                "Could not instantiate aggregate " + aggregateRootType + ". It must have a default constructor!", e);
        }
    }

    @Value.Immutable
    interface AggregateLowerBoundPair<T> {

        static <T> AggregateLowerBoundPair<T> of(T aggregate, OptionalLong revision) {
            return ImmutableAggregateLowerBoundPair.<T>builder()
                .aggregate(aggregate)
                .revision(revision)
                .build();
        }

        T aggregate();

        OptionalLong revision();
    }

    @Value.Immutable
    public interface EventSourcedBasedAggregateLoaderConfig {

        EventStore eventStore();

        @Value.Default
        default SnapshotStrategyFactory snapshotStrategyFactory() {
            return SnapshotStrategyFactory.fixed(SnapshotStrategy.never());
        }

        Optional<SnapshotRepository> snapshotRepository();

        class Builder extends ImmutableEventSourcedBasedAggregateLoaderConfig.Builder {
        }
    }
}
