package com.revents.eventsourcing;

import com.revents.AggregateId;
import com.revents.EventMessage.EventId;
import com.revents.TestAggregate1;
import com.revents.eventsourcing.SnapshotRepository.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

import java.util.UUID;

public abstract class SnapshotRepositoryContract {

    protected abstract SnapshotRepository createSnapshotRepository();

    private SnapshotRepository snapshotRepository;

    static AggregateId<TestAggregate1> anAggregateId() {
        return AggregateId.of(TestAggregate1.class, UUID.randomUUID().toString());
    }

    static Snapshot<TestAggregate1> createSnapshot(AggregateId<TestAggregate1> aggregateId,
                                                   int aggregatePayload,
                                                   long revision) {
        return Snapshot.<TestAggregate1>builder()
            .aggregateId(aggregateId)
            .aggregate(TestAggregate1.createForSnapshotTest(aggregatePayload))
            .revision(revision)
            .basedOnEvent(EventId.create())
            .build();
    }

    @BeforeEach
    void setUp() {
        Hooks.onOperatorDebug();
        snapshotRepository = createSnapshotRepository();
    }

    @Test
    void shouldStoreSnapshot() {
        StepVerifier.create(snapshotRepository.save(createSnapshot(anAggregateId(), 3, 1L)))
            .verifyComplete();
    }

    @Nested
    class AfterOneSnapshotIsSaved {

        AggregateId<TestAggregate1> aggregateId1;
        Snapshot<TestAggregate1> snapshot12;

        @BeforeEach
        void setUp() {
            aggregateId1 = anAggregateId();
            snapshot12 = createSnapshot(aggregateId1, 3, 2L);
            snapshotRepository.save(snapshot12).block();
        }

        @Test
        void shouldLoadSnapshot() {
            StepVerifier.create(snapshotRepository.load(aggregateId1))
                .expectNext(snapshot12)
                .verifyComplete();
        }

        @Test
        void shouldNotFailStoringOlderRevisionButMustLoadNewestOne() {
            StepVerifier.create(snapshotRepository.save(createSnapshot(aggregateId1, 10, 1L)))
                .verifyComplete();

            StepVerifier.create(snapshotRepository.load(aggregateId1))
                .expectNext(snapshot12)
                .verifyComplete();
        }

        @Nested
        class AfterNewRevisionIsSaved {

            Snapshot<TestAggregate1> snapshot13;

            @BeforeEach
            void setUp() {
                aggregateId1 = anAggregateId();
                snapshot13 = createSnapshot(aggregateId1, 4, 3L);
                snapshotRepository.save(snapshot13).block();
            }

            @Test
            void shouldLoadNewerSnapshot() {
                StepVerifier.create(snapshotRepository.load(aggregateId1))
                    .expectNext(snapshot13)
                    .verifyComplete();
            }
        }
    }
}
