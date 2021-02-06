package com.revents.eventsourcing;

import static com.revents.log.TransformContextToMdc.monoLogOnNext;

import com.revents.AggregateId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySnapshotRepository implements SnapshotRepository {

    private static final Logger LOG = LoggerFactory.getLogger(InMemorySnapshotRepository.class);

    private final Map<AggregateId<?>, Snapshot<?>> store = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <T> Mono<Snapshot<T>> load(AggregateId<T> aggregateId) {
        return Mono.justOrEmpty((Snapshot<T>) store.get(aggregateId))
            .transform(monoLogOnNext(snapshot -> LOG.debug("Snapshot loaded={}", snapshot)));
    }

    @Override
    public <T> Mono<Void> save(Snapshot<T> newSnapshot) {
        return Mono.fromCallable(() -> store.compute(newSnapshot.aggregateId(),
            (id, currentSnapshot) -> currentSnapshot == null || currentSnapshot.revision() < newSnapshot.revision()
                ? newSnapshot
                : currentSnapshot))
            .filter(snapshot -> snapshot == newSnapshot)
            .transform(monoLogOnNext(snapshot -> LOG.debug("Creating a snapshot... {}", snapshot)))
            .then();
    }
}
