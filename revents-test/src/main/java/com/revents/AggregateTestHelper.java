package com.revents;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.revents.cqrs.AnnotationAwareCommandMessageDispatcher;
import com.revents.cqrs.DefaultCommandBus;
import com.revents.cqrs.EventStoringCommandInterceptor;
import com.revents.cqrs.LocalCommandGateway;
import com.revents.eventsourcing.EventSourcedBasedAggregateLoader;
import com.revents.eventstore.InMemoryEventStore;
import com.revents.eventstore.InMemoryTokenStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Helper class to test command handling and verify the persisted events.
 */
public final class AggregateTestHelper {

    private final InMemoryEventStore eventStore;
    private final InMemoryTokenStore tokenStore;
    private final CommandGateway commandGateway;
    private final AnnotationAwareCommandMessageDispatcher commandMessageDispatcher;

    private AggregateTestHelper(Class<?>... aggregateClasses) {
        tokenStore = new InMemoryTokenStore();
        eventStore = new InMemoryEventStore(tokenStore);

        commandMessageDispatcher = AnnotationAwareCommandMessageDispatcher.create(handlerConfig -> handlerConfig
            .aggregateLoader(EventSourcedBasedAggregateLoader.create(config -> config
                .eventStore(eventStore)))
            .addAggregateCommandHandlers(aggregateClasses));

        commandGateway = LocalCommandGateway.create(gatewayConfig -> gatewayConfig
            .commandBus(DefaultCommandBus.create(busConfig -> busConfig
                .addCommandMessageInterceptors(new EventStoringCommandInterceptor(eventStore))
                .commandMessageHandler(commandMessageDispatcher)))
            .commandAggregateIdResolver(commandMessageDispatcher));
    }

    private void resetStores() {
        eventStore.reset();
        tokenStore.reset();
    }

    /**
     * Test the behavior of {@code aggregateClass}.
     *
     * @param aggregateClasses the tested aggregate types
     * @return a new helper
     */
    @SuppressWarnings("PMD.JUnit4TestShouldUseTestAnnotation")
    public static AggregateTestHelper testing(Class<?>... aggregateClasses) {
        return new AggregateTestHelper(aggregateClasses);
    }

    /**
     * Define the already persisted events
     * required for the aggregate initialization.
     *
     * @param events to be applied during aggregate load
     * @return the next step
     */
    public Given given(Object... events) {
        return new Given(List.of(events));
    }

    /**
     * Define the already persisted events
     * required for the aggregate initialization.
     *
     * @param events to be applied during aggregate load
     * @return the next phase
     */
    public Given given(List<Object> events) {
        return new Given(events);
    }

    public class Given {

        private final List<Object> events;
        private final Clock clock;

        private Given(List<Object> events) {
            this(events, ReventsClock.system());
        }

        private Given(List<Object> events, Clock clock) {
            this.events = List.copyOf(events);
            this.clock = clock;
        }

        /**
         * Define additional persisted events
         * required for the aggregate initialization.
         *
         * @param events to be also applied during aggregate load
         * @return a new given phase
         */
        public Given andGiven(Object... events) {
            return andGiven(List.of(events));
        }

        /**
         * Define additional persisted events
         * required for the aggregate initialization.
         *
         * @param events to be also applied during aggregate load
         * @return a new given phase
         */
        public Given andGiven(List<Object> events) {
            return new Given(ImmutableList.builder()
                .addAll(this.events)
                .addAll(events)
                .build(), clock);
        }

        /**
         * Override system clock.
         *
         * @param clock the new clock to be used by the system for this particular test
         * @return a new given phase
         */
        public Given andGivenSystemClock(Clock clock) {
            return new Given(events, clock);
        }

        /**
         * Override the current time.
         *
         * <p>{@code ReventsClock.system().getZone()} is used as zone across the whole system</p>
         *
         * @param moment the fixed instant to be used by the system for this particular test
         * @return a new given phase
         */
        public Given andGivenCurrentTime(Instant moment) {
            return andGivenSystemClock(Clock.fixed(moment, ReventsClock.system().getZone()));
        }

        /**
         * Override the current time.
         *
         * <p>The {@code ZoneId} defined by {@code zonedDateTime} is used as zone across the whole system</p>
         *
         * @param zonedDateTime the fixed moment to be used by the system for this particular test
         * @return a new given phase
         */
        public Given andGivenCurrentTime(ZonedDateTime zonedDateTime) {
            return andGivenSystemClock(Clock.fixed(zonedDateTime.toInstant(), zonedDateTime.getZone()));
        }

        /**
         * Fire the command need to be tested.
         *
         * @param command to test its handler
         * @return the next phase
         */
        public When when(Object command) {
            return new When(command);
        }

        public class When {

            private final Object command;

            private When(Object command) {
                this.command = requireNonNull(command);
            }

            /**
             * Consume the new persisted events emitted by the tested command handler.
             *
             * @param persistentEventsConsumer to consume the events
             * @return the actual {@link Duration} the verification took
             */
            @CanIgnoreReturnValue
            public Duration thenExpect(Consumer<List<Object>> persistentEventsConsumer) {
                return processCommand()
                    .map(CommandResult::allEventMessages)
                    .flatMapMany(Flux::fromIterable)
                    .<Object>map(EventMessage::payload)
                    .collectList()
                    .as(StepVerifier::create)
                    .consumeNextWith(persistentEventsConsumer)
                    .verifyComplete();
            }

            /**
             * Consume the new persisted event emitted by the tested command handler.
             *
             * @param persistentEventConsumer to consume the event
             * @return the actual {@link Duration} the verification took
             */
            @CanIgnoreReturnValue
            public <T> Duration thenExpectSingle(Class<T> clazz, Consumer<? super T> persistentEventConsumer) {
                return processCommand()
                    .map(CommandResult::allEventMessages)
                    .flatMapMany(Flux::fromIterable)
                    .<Object>map(EventMessage::payload)
                    .single()
                    .cast(clazz)
                    .as(StepVerifier::create)
                    .consumeNextWith(persistentEventConsumer)
                    .verifyComplete();
            }

            /**
             * Return the new persisted events emitted by the tested command handler.
             *
             * @return the new events
             */
            public List<Object> thenReturnAll() {
                AtomicReference<List<Object>> extractedEvents = new AtomicReference<>();
                thenExpect(extractedEvents::set);
                return extractedEvents.get();
            }

            /**
             * Return the new persisted event emitted by the tested command handler.
             *
             * @return the new event
             */
            public <T> T thenReturnSingle(Class<T> clazz) {
                AtomicReference<T> extractedEvents = new AtomicReference<>();
                thenExpectSingle(clazz, extractedEvents::set);
                return extractedEvents.get();
            }

            /**
             * Consume the error thrown by the tested command handler.
             *
             * @param errorConsumer to consume the error
             */
            public void thenExpectError(Consumer<Throwable> errorConsumer) {
                processCommand()
                    .as(StepVerifier::create)
                    .consumeErrorWith(errorConsumer)
                    .verify();
            }

            private Mono<CommandResult> processCommand() {
                resetStores();
                return commandMessageDispatcher.aggregateIdOf(command)
                    .flatMap(aggregateId -> Flux.fromIterable(events)
                        .<EventMessage<?>>map(event -> eventToMessage(aggregateId, event))
                        .collectList()
                        .flatMap(eventMessages -> eventStore.store(aggregateId, eventMessages, OptionalLong.empty())))
                    .then(commandGateway.sendWithResult(command))
                    .contextWrite(context -> context.put(Clock.class, clock));
            }

            private EventMessage<?> eventToMessage(AggregateId<?> aggregateId, Object event) {
                return EventMessage.builder()
                    .metaData(EventMessage.EventMetaData.builder()
                        .aggregateId(aggregateId)
                        .build())
                    .payload(event)
                    .build();
            }
        }
    }
}
