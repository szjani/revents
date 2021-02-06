package com.revents;

import static org.assertj.core.api.Assertions.assertThat;

import com.revents.EventMessage.EventMetaData;
import com.revents.EventProcessor.EventProcessorId;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Stream;

public abstract class EventStoreContract {

    protected abstract EventStore createEventStore();

    private EventStore eventStore;

    @BeforeEach
    void setUp() {
        Hooks.onOperatorDebug();
        eventStore = createEventStore();
    }

    static <P> ImmutableEventMessage<P> createNewEventMessage(AggregateId<TestAggregate1> aggregateId, P payload) {
        return EventMessage.<P>builder()
            .payload(payload)
            .metaData(EventMetaData.builder()
                .aggregateId(aggregateId)
                .build())
            .build();
    }

    static <P> ImmutableEventMessage<P> sameEventButWithRevision(EventMessage<P> eventMessage, long revision) {
        return ImmutableEventMessage.copyOf(eventMessage)
            .withMetaData(ImmutableEventMetaData.copyOf(eventMessage.metaData())
                .withRevision(revision));
    }

    static AggregateId<TestAggregate1> anAggregateId() {
        return AggregateId.of(TestAggregate1.class, UUID.randomUUID().toString());
    }

    @Test
    void shouldStoreFirstEventMessage() {
        var aggregateId = anAggregateId();
        String payload = "hello";
        ImmutableEventMessage<String> eventMessage = createNewEventMessage(aggregateId, payload);
        Mono<List<EventMessage<?>>> result = eventStore.store(
            aggregateId,
            List.of(eventMessage),
            OptionalLong.empty());

        StepVerifier.create(result)
            .consumeNextWith(persistedEventMessages -> assertThat(persistedEventMessages)
                .hasSize(1)
                .first()
                .isEqualTo(sameEventButWithRevision(eventMessage, 1L)))
            .verifyComplete();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class AfterFirstEventIsStored {

        AggregateId<TestAggregate1> aggregateId1;
        AggregateId<TestAggregate1> aggregateId2;
        EventMessage<String> newEventMessage11;
        EventMessage<String> newEventMessage21;

        @BeforeEach
        void setUp() {
            aggregateId1 = anAggregateId();
            newEventMessage11 = createNewEventMessage(aggregateId1, "payload1_1");
            eventStore.store(
                aggregateId1,
                    List.of(newEventMessage11),
                    OptionalLong.empty())
                .block();

            aggregateId2 = anAggregateId();
            newEventMessage21 = createNewEventMessage(aggregateId2, "payload2_1");
            eventStore.store(
                aggregateId2,
                List.of(newEventMessage21),
                OptionalLong.empty())
                .block();
        }

        Stream<OptionalLong> findAllEventRevision() {
            return Stream.of(OptionalLong.empty(), OptionalLong.of(0L));
        }

        @ParameterizedTest
        @MethodSource("findAllEventRevision")
        void shouldReadOneAggregateEvent(OptionalLong revision) {
            StepVerifier.create(eventStore.eventsForAggregate(aggregateId1, revision))
                .expectNext(sameEventButWithRevision(newEventMessage11, 1L))
                .verifyComplete();
        }

        @Test
        void shouldFailWithRevisionMismatchIfProvidedRevisionIsWrong() {
            int expectedRevision = 42;
            Mono<List<EventMessage<?>>> storeWithWrongRevision = eventStore.store(
                aggregateId1,
                List.of(createNewEventMessage(aggregateId1, "wrong")),
                OptionalLong.of(expectedRevision));

            StepVerifier.create(storeWithWrongRevision)
                .verifyErrorSatisfies(e -> assertThat(e)
                    .isInstanceOf(RevisionMismatchException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(RevisionMismatchException.class))
                    .satisfies(exp -> {
                        assertThat(exp.getAggregateId()).isSameAs(aggregateId1);
                        assertThat(exp.getExpectedRevision()).isEqualTo(expectedRevision);
                    }));
        }

        @Nested
        @TestInstance(TestInstance.Lifecycle.PER_CLASS)
        class AfterSecondEventIsStored {

            EventMessage<String> newEventMessage12;

            @BeforeEach
            void setUp() {
                newEventMessage12 = createNewEventMessage(aggregateId1, "payload1_2");
                eventStore.store(
                    aggregateId1,
                    List.of(newEventMessage12),
                    OptionalLong.empty())
                    .block();
            }

            Stream<OptionalLong> findAllEventRevision() {
                return Stream.of(OptionalLong.empty(), OptionalLong.of(0L));
            }

            @ParameterizedTest
            @MethodSource("findAllEventRevision")
            void shouldReadTwoAggregateEvents(OptionalLong revision) {
                StepVerifier.create(eventStore.eventsForAggregate(aggregateId1, revision))
                    .expectNext(sameEventButWithRevision(newEventMessage11, 1L))
                    .expectNext(sameEventButWithRevision(newEventMessage12, 2L))
                    .verifyComplete();
            }

            @Test
            void shouldSkipFirstAggregateEvent() {
                StepVerifier.create(eventStore.eventsForAggregate(aggregateId1, OptionalLong.of(1L)))
                    .expectNext(sameEventButWithRevision(newEventMessage12, 2L))
                    .verifyComplete();
            }

            @Test
            void shouldReadAllViaEventStream() {
                EventStreamSubscription eventStreamSubscription = eventStore.readEventStream(StreamReadRequest.builder()
                    .infinite(true)
                    .eventProcessorId(EventProcessorId.of("any"))
                    .build());

                StepVerifier.create(eventStreamSubscription.events())
                    .expectNext(sameEventButWithRevision(newEventMessage11, 1L))
                    .then(() -> eventStreamSubscription.acknowledge(newEventMessage11).subscribe())
                    .expectNext(sameEventButWithRevision(newEventMessage21, 1L))
                    .then(() -> eventStreamSubscription.acknowledge(newEventMessage21).subscribe())
                    .expectNext(sameEventButWithRevision(newEventMessage12, 2L))
                    .then(() -> eventStreamSubscription.acknowledge(newEventMessage12).subscribe())
                    .then(() -> eventStore.store(
                        aggregateId2,
                        List.of(createNewEventMessage(aggregateId2, "newEvent")),
                        OptionalLong.empty())
                        .subscribe())
                    .consumeNextWith(eventMessage4 -> {
                        assertThat(eventMessage4.payload()).isEqualTo("newEvent");
                        assertThat(eventMessage4.metaData().revision()).hasValue(2L);
                    })
                    .thenCancel()
                    .verify();
            }

            @Test
            void shouldReadByFixStreamName() {
                var testEvent2 = ImmutableTestEvent2.builder()
                    .payload(2)
                    .build();
                eventStore.store(
                        aggregateId1,
                        List.of(createNewEventMessage(aggregateId1, testEvent2)),
                        OptionalLong.empty())
                    .block();

                StreamReadRequest streamReadRequest = StreamReadRequest.builder()
                    .streamName(StreamName.of("TestEvent2"))
                    .eventProcessorId(EventProcessorId.of("any"))
                    .build();
                eventStore.readEventStream(streamReadRequest).events()
                    .as(StepVerifier::create)
                    .consumeNextWith(eventMessage -> assertThat(eventMessage.payload()).isEqualTo(testEvent2))
                    .thenCancel()
                    .verify();
            }

            @Test
            void shouldReadByDynamicStreamName() {
                var testEvent1 = ImmutableTestEvent1.builder()
                    .aggregateId("foo")
                    .payload(2)
                    .build();
                eventStore.store(
                    aggregateId1,
                    List.of(createNewEventMessage(aggregateId1, testEvent1)),
                    OptionalLong.empty())
                    .block();

                var readRequest = StreamReadRequest.builder()
                    .eventProcessorId(EventProcessorId.of("any"))
                    .streamName(StreamName.of("TestAggregate1-foo"))
                    .build();

                eventStore.readEventStream(readRequest).events()
                    .as(StepVerifier::create)
                    .consumeNextWith(eventMessage -> assertThat(eventMessage.payload()).isEqualTo(testEvent1))
                    .thenCancel()
                    .verify();
            }
        }
    }
}
