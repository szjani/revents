package com.revents.eventsourcing;

import com.revents.AggregateId;

public interface SnapshotStrategyFactory {

    <T> SnapshotStrategy createFor(AggregateId<T> aggregateId);

    /**
     * The returned {@link SnapshotStrategyFactory} always return
     * the given {@code snapshotStrategy}, regardless of the given {@code aggregateId}.
     *
     * @param snapshotStrategy the snapshot strategy need to be returned all the time
     * @return a new SnapshotStrategyFactory
     */
    static SnapshotStrategyFactory fixed(SnapshotStrategy snapshotStrategy) {
        return new SnapshotStrategyFactory() {

            @Override
            public <T> SnapshotStrategy createFor(AggregateId<T> aggregateId) {
                return snapshotStrategy;
            }
        };
    }
}
