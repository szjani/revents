package com.revents.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;

import com.revents.AggregateId;
import com.revents.EventMessage;
import com.revents.EventMessage.EventMetaData;
import com.revents.eventsourcing.SnapshotRepository.Snapshot;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@SuppressWarnings("PMD.UseUtilityClass")
class SnapshotStrategyTest {

    static final OffsetDateTime A_REFERENCE_DATE_TIME = OffsetDateTime.of(2020, 12, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Nested
    class DurationBased {

        @Test
        void shouldCreateSnapshotIfGivenSnapshotIsOld() {
            var event = createEventCreatedAt(A_REFERENCE_DATE_TIME.plus(Duration.ofMinutes(2)));

            Flux<Tuple2<EventMessage<?>, Boolean>> result = SnapshotStrategy.every(Duration.ofMinutes(1))
                .needSnapshot(Flux.just(event),
                    createSnapshotCreatedAt(A_REFERENCE_DATE_TIME));

            StepVerifier.create(result)
                .consumeNextWith(tuple -> {
                    assertThat(tuple.getT1()).isSameAs(event);
                    assertThat(tuple.getT2()).isTrue();
                })
                .verifyComplete();
        }

        @Test
        void shouldNotCreateSnapshotIfLastSnapshotIsNew() {
            var event = createEventCreatedAt(A_REFERENCE_DATE_TIME.plus(Duration.ofMinutes(1)));

            Flux<Tuple2<EventMessage<?>, Boolean>> result = SnapshotStrategy.every(Duration.ofMinutes(2))
                .needSnapshot(Flux.just(event),
                    createSnapshotCreatedAt(A_REFERENCE_DATE_TIME));

            StepVerifier.create(result)
                .consumeNextWith(tuple -> {
                    assertThat(tuple.getT1()).isSameAs(event);
                    assertThat(tuple.getT2()).isFalse();
                })
                .verifyComplete();
        }

        @Test
        void shouldCreateSnapshotIfFirstEventIsTooOld() {
            var event1 = createEventCreatedAt(A_REFERENCE_DATE_TIME);
            var event2 = createEventCreatedAt(A_REFERENCE_DATE_TIME.plus(Duration.ofMinutes(2)));

            Flux<Tuple2<EventMessage<?>, Boolean>> result = SnapshotStrategy.every(Duration.ofMinutes(1))
                .needSnapshot(Flux.just(event1, event2), null);

            StepVerifier.create(result)
                .consumeNextWith(tuple -> {
                    assertThat(tuple.getT1()).isSameAs(event1);
                    assertThat(tuple.getT2()).isFalse();
                })
                .consumeNextWith(tuple -> {
                    assertThat(tuple.getT1()).isSameAs(event2);
                    assertThat(tuple.getT2()).isTrue();
                })
                .verifyComplete();
        }

        @Test
        void shouldCreateMultipleSnapshots() {
            var event1 = createEventCreatedAt(A_REFERENCE_DATE_TIME);
            var event2 = createEventCreatedAt(A_REFERENCE_DATE_TIME.plus(Duration.ofMinutes(3)));
            var event3 = createEventCreatedAt(A_REFERENCE_DATE_TIME.plus(Duration.ofMinutes(4)));
            var event4 = createEventCreatedAt(A_REFERENCE_DATE_TIME.plus(Duration.ofMinutes(7)));

            Flux<Tuple2<EventMessage<?>, Boolean>> result = SnapshotStrategy.every(Duration.ofMinutes(2))
                .needSnapshot(Flux.just(event1, event2, event3, event4), null);

            StepVerifier.create(result)
                .consumeNextWith(tuple -> {
                    assertThat(tuple.getT1()).isSameAs(event1);
                    assertThat(tuple.getT2()).isFalse();
                })
                .consumeNextWith(tuple -> {
                    assertThat(tuple.getT1()).isSameAs(event2);
                    assertThat(tuple.getT2()).isTrue();
                })
                .consumeNextWith(tuple -> {
                    assertThat(tuple.getT1()).isSameAs(event3);
                    assertThat(tuple.getT2()).isFalse();
                })
                .consumeNextWith(tuple -> {
                    assertThat(tuple.getT1()).isSameAs(event4);
                    assertThat(tuple.getT2()).isTrue();
                })
                .verifyComplete();
        }
    }

    static EventMessage<String> createEventCreatedAt(OffsetDateTime created) {
        return EventMessage.<String>builder()
            .metaData(EventMetaData.builder()
                .withClock(Clock.fixed(created.toInstant(), created.getOffset()))
                .revision(0)
                .aggregateId(AggregateId.of(String.class, "anyId"))
                .build())
            .payload("anyPayload")
            .build();
    }

    static Snapshot<String> createSnapshotCreatedAt(OffsetDateTime created) {
        return Snapshot.<String>builder()
            .created(created)
            .aggregate("any")
            .aggregateId(AggregateId.of(String.class, "anyId"))
            .basedOnEvent(EventMessage.EventId.create())
            .revision(1L)
            .build();
    }
}
