package com.revents.eventsourcing;

import com.revents.AggregateId;
import com.revents.EventMessage.EventId;
import com.revents.serialization.SerializedTypeMapper;
import com.revents.serialization.Serializer;
import io.r2dbc.spi.Row;
import org.immutables.value.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.function.UnaryOperator;

public class SpringR2dbcSnapshotRepository implements SnapshotRepository {

    private static final String SNAPSHOT_SELECT = "SELECT * FROM snapshot WHERE aggregate_type=$1 AND aggregate_id=$2";
    private static final String SNAPSHOT_UPDATE = "UPDATE snapshot "
        + "SET aggregate=$1, based_on_event=$2, revision=$3, created=$4 "
        + "WHERE aggregate_type=$5 AND aggregate_id=$6 AND revision=$7";
    private static final String SNAPSHOT_INSERT = "INSERT INTO snapshot "
        + "(aggregate_type, aggregate_id, aggregate, based_on_event, revision, created) "
        + "VALUES ($1, $2, $3, $4, $5, $6)";

    private final DatabaseClient databaseClient;
    private final ReactiveTransactionManager reactiveTransactionManager;
    private final Serializer serializer;
    private final SerializedTypeMapper serializedTypeMapper;

    public static SpringR2dbcSnapshotRepository create(
        UnaryOperator<SpringR2dbcSnapshotRepositoryConfig.Builder> init) {

        return new SpringR2dbcSnapshotRepository(init.apply(new SpringR2dbcSnapshotRepositoryConfig.Builder()).build());
    }

    private SpringR2dbcSnapshotRepository(SpringR2dbcSnapshotRepositoryConfig config) {
        databaseClient = config.databaseClient();
        reactiveTransactionManager = config.reactiveTransactionManager();
        serializer = config.serializer();
        serializedTypeMapper = config.serializedTypeMapper();
    }

    @Override
    public <T> Mono<Snapshot<T>> load(AggregateId<T> aggregateId) {
        return databaseClient.sql(SNAPSHOT_SELECT)
            .bind("$1", serializedTypeMapper.classToStringEagerly(aggregateId.aggregateRootType()))
            .bind("$2", aggregateId.aggregateRootId())
            .map(this::<T>toSnapshot)
            .one();
    }

    @Override
    public <T> Mono<Void> save(Snapshot<T> newSnapshot) {
        return Mono.just(serializedTypeMapper.classToStringEagerly(newSnapshot.aggregateId().aggregateRootType()))
            .flatMap(serializedType -> load(newSnapshot.aggregateId())
                .switchIfEmpty(databaseClient.sql(SNAPSHOT_INSERT)
                    .bind("$1", serializedType)
                    .bind("$2", newSnapshot.aggregateId().aggregateRootId())
                    .bind("$3", serializer.serialize(newSnapshot.aggregate()))
                    .bind("$4", newSnapshot.basedOnEvent().rawId())
                    .bind("$5", newSnapshot.revision())
                    .bind("$6", newSnapshot.created())
                    .fetch()
                    .rowsUpdated()
                    .thenReturn(newSnapshot))
                .filter(currentSnapshot -> currentSnapshot.revision() < newSnapshot.revision())
                .flatMap(currentSnapshot -> databaseClient.sql(SNAPSHOT_UPDATE)
                    .bind("$1", serializer.serialize(newSnapshot.aggregate()))
                    .bind("$2", newSnapshot.basedOnEvent().rawId())
                    .bind("$3", newSnapshot.revision())
                    .bind("$4", newSnapshot.created())
                    .bind("$5", serializedType)
                    .bind("$6", newSnapshot.aggregateId().aggregateRootId())
                    .bind("$7", currentSnapshot.revision())
                    .fetch()
                    .rowsUpdated()))
            .then()
            .as(TransactionalOperator.create(reactiveTransactionManager)::transactional);
    }

    private <T> Snapshot<T> toSnapshot(Row row) {
        Class<T> aggregateType = serializedTypeMapper.stringToClassEagerly(
            row.get("aggregate_type", String.class));
        return Snapshot.<T>builder()
            .aggregateId(AggregateId.of(
                aggregateType,
                row.get("aggregate_id", String.class)))
            .aggregate(serializer.deserialize(
                row.get("aggregate", String.class),
                aggregateType))
            .basedOnEvent(EventId.of(row.get("based_on_event", String.class)))
            .revision(row.get("revision", Long.class))
            .created(row.get("created", OffsetDateTime.class))
            .build();
    }

    @Value.Immutable
    public interface SpringR2dbcSnapshotRepositoryConfig {

        DatabaseClient databaseClient();

        ReactiveTransactionManager reactiveTransactionManager();

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

        class Builder extends ImmutableSpringR2dbcSnapshotRepositoryConfig.Builder {
        }
    }
}
