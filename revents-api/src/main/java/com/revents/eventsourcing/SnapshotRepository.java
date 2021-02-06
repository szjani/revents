package com.revents.eventsourcing;

import com.revents.AggregateId;
import com.revents.EventMessage.EventId;
import org.immutables.value.Value;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface SnapshotRepository {

    <T> Mono<Snapshot<T>> load(AggregateId<T> aggregateId);

    <T> Mono<Void> save(Snapshot<T> snapshot);

    @Value.Immutable
    interface Snapshot<T> {

        AggregateId<T> aggregateId();

        T aggregate();

        EventId basedOnEvent();

        long revision();

        OffsetDateTime created();

        static <T> ImmutableSnapshot.Builder<T> builder() {
            return ImmutableSnapshot.builder();
        }
    }
}
