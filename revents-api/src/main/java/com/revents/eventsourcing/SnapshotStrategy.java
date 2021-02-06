package com.revents.eventsourcing;

import com.revents.EventMessage;
import com.revents.eventsourcing.SnapshotRepository.Snapshot;
import org.immutables.value.Value;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface SnapshotStrategy {

    <T> Flux<Tuple2<EventMessage<?>, Boolean>> needSnapshot(Flux<EventMessage<?>> events,
                                                            @Nullable Snapshot<T> lastSnapshot);

    /**
     * Create a {@code SnapshotStrategy} that always wants to create snapshots.
     *
     * @return a new strategy instance
     */
    static SnapshotStrategy always() {
        return new SnapshotStrategy() {

            @Override
            public <T> Flux<Tuple2<EventMessage<?>, Boolean>> needSnapshot(Flux<EventMessage<?>> events,
                                                                           @Nullable Snapshot<T> lastSnapshot) {
                return events.map(eventMessage -> Tuples.of(eventMessage, true));
            }
        };
    }

    /**
     * Create a {@code SnapshotStrategy} that never wants to create snapshots.
     *
     * @return a new strategy instance
     */
    static SnapshotStrategy never() {
        return new SnapshotStrategy() {

            @Override
            public <T> Flux<Tuple2<EventMessage<?>, Boolean>> needSnapshot(Flux<EventMessage<?>> events,
                                                                           @Nullable Snapshot<T> lastSnapshot) {
                return events.map(eventMessage -> Tuples.of(eventMessage, false));
            }
        };
    }

    /**
     * Create a {@code SnapshotStrategy} that wants to create snapshot
     * if at least {@code count} number of events stored since the creation
     * of the last snapshot.
     *
     * @param count expected number of events between snapshots
     * @return a new strategy instance
     */
    static SnapshotStrategy every(long count) {
        return new SnapshotStrategy() {

            @Override
            public <T> Flux<Tuple2<EventMessage<?>, Boolean>> needSnapshot(Flux<EventMessage<?>> events,
                                                                           @Nullable Snapshot<T> lastSnapshot) {
                return events.index((index, eventMessage) -> Tuples.of(eventMessage, index % count == 0));
            }
        };
    }

    /**
     * Create a {@code SnapshotStrategy} that wants to create snapshot
     * if at least {@code duration} time spent since the creation
     * of the last snapshot.
     *
     * @param duration expected duration between snapshots
     * @return a new strategy instance
     */
    static SnapshotStrategy every(Duration duration) {
        return new SnapshotStrategy() {

            @Override
            public <T> Flux<Tuple2<EventMessage<?>, Boolean>> needSnapshot(Flux<EventMessage<?>> events,
                                                                           @Nullable Snapshot<T> lastSnapshot) {
                return events
                    .scan(TimeBasedWrapper.basedOn(duration, lastSnapshot), TimeBasedWrapper::calculateNext)
                    .skip(1)
                    .map(x -> Tuples.of(x.eventMessage().get(), x.snapshotNeeded()));
            }
        };
    }

    @Value.Immutable
    interface TimeBasedWrapper {

        Duration expectedSnapshotWindow();

        Optional<OffsetDateTime> referencePoint();

        Optional<EventMessage<?>> eventMessage();

        static <T> TimeBasedWrapper basedOn(Duration snapshotWindow, @Nullable Snapshot<T> lastSnapshot) {
            return ImmutableTimeBasedWrapper.builder()
                .expectedSnapshotWindow(snapshotWindow)
                .referencePoint(Optional.ofNullable(lastSnapshot).map(Snapshot::created))
                .build();
        }

        @Value.Default
        default boolean snapshotNeeded() {
            return false;
        }

        default TimeBasedWrapper calculateNext(EventMessage<?> nextEventMessage) {
            boolean needSnapshot = referencePoint()
                .map(reference -> Duration.between(reference, nextEventMessage.metaData().created()))
                .map(spentTime -> expectedSnapshotWindow().minus(spentTime).isNegative())
                .orElse(false);

            return ImmutableTimeBasedWrapper.builder()
                .expectedSnapshotWindow(expectedSnapshotWindow())
                .eventMessage(nextEventMessage)
                .snapshotNeeded(needSnapshot)
                .referencePoint(needSnapshot
                    ? nextEventMessage.metaData().created()
                    : referencePoint().orElse(nextEventMessage.metaData().created()))
                .build();
        }
    }
}
