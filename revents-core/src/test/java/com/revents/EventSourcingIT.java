package com.revents;

import static com.revents.log.TransformContextToMdc.monoLogOnSubscribe;
import static com.revents.log.TransformContextToMdc.monoLogOnSuccess;
import static org.assertj.core.api.Assertions.assertThat;

import com.revents.cqrs.AnnotationAwareCommandMessageDispatcher;
import com.revents.cqrs.DefaultCommandBus;
import com.revents.cqrs.EventStoringCommandInterceptor;
import com.revents.cqrs.LocalCommandGateway;
import com.revents.cqrs.LoggingMessageInterceptor;
import com.revents.eventsourcing.EventSourcedBasedAggregateLoader;
import com.revents.eventstore.InMemoryEventStore;
import com.revents.eventstore.InMemoryTokenStore;
import com.revents.eventstore.TrackingEventProcessor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.OptionalLong;

class EventSourcingIT {

    private static final Logger LOG = LoggerFactory.getLogger(EventSourcingIT.class);

    CommandGateway commandGateway;
    EventStore eventStore;
    AggregateLoader aggregateLoader;
    TestEventHandler1 testEventHandler1;
    TrackingEventProcessor trackingEventProcessor1;

    @BeforeEach
    void setUp() {
        eventStore = new InMemoryEventStore(new InMemoryTokenStore());
        aggregateLoader = EventSourcedBasedAggregateLoader.create(config -> config.eventStore(eventStore));
        var commandMessageDispatcher = AnnotationAwareCommandMessageDispatcher.create(config -> config
            .aggregateLoader(aggregateLoader)
            .scanAggregateClassesIn("com.revents"));

        CommandBus commandBus = DefaultCommandBus.create(busConfig -> busConfig
            .addCommandMessageInterceptors(
                new LoggingMessageInterceptor<>(),
                new EventStoringCommandInterceptor(eventStore))
            .commandMessageHandler(commandMessageDispatcher));

        commandGateway = LocalCommandGateway.create(config -> config
            .commandAggregateIdResolver(commandMessageDispatcher)
            .commandBus(commandBus));

        testEventHandler1 = new TestEventHandler1();

        trackingEventProcessor1 = TrackingEventProcessor.create(config -> config
            .eventStore(eventStore)
            .addEventMessageDispatchInterceptors((message, chain) -> Mono.just(1)
                .transform(monoLogOnSubscribe(x -> LOG.info("Event dispatch interceptor - before")))
                .flatMap(x -> chain.handle(message))
                .transform(monoLogOnSuccess(x -> LOG.info("Event dispatch interceptor - after"))))
            .annotationBasedEventMessageHandler(messageHandlerConfig -> messageHandlerConfig
                .addEventMessageHandlerInterceptors((message, chain) -> Mono.just(1)
                    .transform(monoLogOnSubscribe(x -> LOG.info("Event handler interceptor - before")))
                    .flatMap(x -> chain.handle(message))
                    .transform(monoLogOnSuccess(x -> LOG.info("Event handler interceptor - after"))))
                .addEventHandlers(testEventHandler1)));
        trackingEventProcessor1.run();
    }

    @AfterEach
    void tearDown() {
        trackingEventProcessor1.stop();
    }

    @Test
    void shouldCreateNewAggregateInstance() {
        int commandPayload = 10;
        String expectedId = "expectedId";
        StepVerifier.create(commandGateway.send(ImmutableTestCommand1.builder()
                .useThisId(expectedId)
                .payload(commandPayload)
                .build()))
            .verifyComplete();

        StepVerifier.create(eventStore.eventsForAggregate(
                AggregateId.of(TestAggregate1.class, expectedId), OptionalLong.empty()))
            .consumeNextWith(eventMessage -> assertThat(eventMessage.payload())
                .isInstanceOf(TestEvent1.class)
                .asInstanceOf(InstanceOfAssertFactories.type(TestEvent1.class))
                .satisfies(testEvent1 -> assertThat(testEvent1.payload()).isEqualTo(commandPayload)))
            .verifyComplete();
    }

    @Test
    void shouldHandleErrorFromControllerCommandHandler() {
        StepVerifier.create(commandGateway.send(ImmutableTestCommand1.builder()
            .useThisId("any")
            .payload(42)
            .build()))
            .verifyErrorSatisfies(e -> assertThat(e)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payload must not be 42"));
    }

    @Nested
    class AggregateCreated {

        int initNumber = 10;
        String aggregateId = "expectedId";

        @BeforeEach
        void setUp() {
            StepVerifier.create(commandGateway.send(ImmutableTestCommand1.builder()
                .useThisId(aggregateId)
                .payload(initNumber)
                .build()))
                .verifyComplete();
        }

        @Test
        void shouldGetExceptionThrownFromCommandHandler() {
            StepVerifier.create(commandGateway.send(ImmutableTestCommand2.builder()
                    .id(aggregateId)
                    .payload(3)
                    .build()))
                .verifyErrorMessage("10 cannot be divided by 3");
            StepVerifier.create(aggregateLoader.load(AggregateId.of(TestAggregate1.class, aggregateId)))
                .expectNextCount(1)
                .verifyComplete();
        }

        @Test
        void shouldProcessEventInEventHandler() {
            StepVerifier.create(testEventHandler1.eventStream1.asFlux())
                .expectNextCount(1)
                .thenCancel()
                .verify();
        }

        @Test
        void shouldProcessEventAsParentTypeInEventHandler() {
            StepVerifier.create(testEventHandler1.allEventStream.asFlux())
                .expectNextCount(1)
                .thenCancel()
                .verify();
        }

        @Test
        void shouldHandleSecondCommand() {
            StepVerifier.create(commandGateway.send(ImmutableTestCommand2.builder()
                    .id(aggregateId)
                    .payload(5)
                    .build()))
                .verifyComplete();
        }

        @Nested
        class SecondCommandHandled {

            @BeforeEach
            void setUp() {
                StepVerifier.create(commandGateway.send(ImmutableTestCommand2.builder()
                    .id(aggregateId)
                    .payload(5)
                    .build()))
                    .verifyComplete();
            }

            @Test
            void shouldFailIfExpectedRevisionIsWrong() {
                StepVerifier.create(commandGateway.send(ImmutableTestCommand3.builder()
                    .id(aggregateId)
                    .payload(1)
                    .revision(7)
                    .build()))
                    .verifyErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(RevisionMismatchException.class)
                        .asInstanceOf(InstanceOfAssertFactories.type(RevisionMismatchException.class))
                        .satisfies(exp -> {
                            assertThat(exp.getAggregateId())
                                .isEqualTo(AggregateId.of(TestAggregate1.class, aggregateId));
                            assertThat(exp.getExpectedRevision()).isEqualTo(7);
                        }));
            }

            @Test
            void shouldSuccessIfExpectedRevisionIsCorrect() {
                StepVerifier.create(commandGateway.send(ImmutableTestCommand3.builder()
                    .id(aggregateId)
                    .payload(1)
                    .revision(2)
                    .build()))
                    .verifyComplete();

                StepVerifier.create(commandGateway.send(ImmutableTestCommand3.builder()
                    .id(aggregateId)
                    .payload(1)
                    .revision(3)
                    .build()))
                    .verifyComplete();
            }
        }
    }

}
