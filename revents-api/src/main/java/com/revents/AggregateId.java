package com.revents;

import org.immutables.value.Value;

@Value.Immutable
public interface AggregateId<T> {

    Class<T> aggregateRootType();

    String aggregateRootId();

    /**
     * Create a wrapped {@link AggregateId} from the given parameters.
     *
     * @param aggregateRootType type of the aggregate root
     * @param aggregateRootId id of the aggregate
     * @param <T> type of the aggregate root
     * @return the wrapper object
     */
    static <T> AggregateId<T> of(Class<T> aggregateRootType, String aggregateRootId) {
        return ImmutableAggregateId.<T>builder()
            .aggregateRootType(aggregateRootType)
            .aggregateRootId(aggregateRootId)
            .build();
    }
}
