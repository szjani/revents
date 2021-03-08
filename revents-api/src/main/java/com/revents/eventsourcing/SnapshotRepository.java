package com.revents.eventsourcing;

import com.revents.AggregateId;
import com.revents.EventMessage.EventId;
import com.revents.ReventsClock;
import org.immutables.value.Value;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public interface SnapshotRepository {

    <T> Mono<Snapshot<T>> load(AggregateId<T> aggregateId);

    <T> Mono<Void> save(Snapshot<T> snapshot);

    @Value.Immutable
    interface Snapshot<T> {

        AggregateId<T> aggregateId();

        T aggregate();

        EventId basedOnEvent();

        long revision();

        @Value.Default
        default OffsetDateTime created() {
            return OffsetDateTime.now(ReventsClock.system()).truncatedTo(ChronoUnit.MILLIS);
        }

        static <T> ImmutableSnapshot.Builder<T> builder() {
            return ImmutableSnapshot.builder();
        }
    }
}
