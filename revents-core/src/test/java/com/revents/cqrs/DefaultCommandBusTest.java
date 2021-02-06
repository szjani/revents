package com.revents.cqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.revents.AggregateId;
import com.revents.AggregateLoader;
import com.revents.CommandBus;
import com.revents.CommandContext;
import com.revents.CommandHandler;
import com.revents.CommandMessage;
import com.revents.CommandMessage.CommandMetaData;
import com.revents.ImmutableCommandMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DefaultCommandBusTest {

    private static final AggregateId<Aggregate1> AN_AGGREGATE_ID = AggregateId.of(Aggregate1.class, "abc123");

    CommandBus commandBus;

    @Mock
    AggregateLoader aggregateLoader;

    @BeforeEach
    void setUp() {
        commandBus = DefaultCommandBus.create(busConfig -> busConfig
            .annotationBasedCommandMessageHandler(annotationConfig -> annotationConfig
                .aggregateLoader(aggregateLoader)
                .addAggregateCommandHandlers(Aggregate1.class)));
    }

    @Test
    void shouldCallConstructor() {
        CreateAggregate command = new CreateAggregate();
        ImmutableCommandMessage<CreateAggregate> commandMessage = CommandMessage.<CreateAggregate>builder()
            .metaData(CommandMetaData.builder()
                .build())
            .payload(command)
            .build();

        StepVerifier.create(commandBus.publish(commandMessage))
            .expectNextCount(1)
            .verifyComplete();
        assertThat(command.dispatched).isTrue();
    }

    @Test
    void shouldCallConstructorWithChildClassInstance() {
        CreateAggregateSub command = new CreateAggregateSub();
        ImmutableCommandMessage<CreateAggregateSub> commandMessage = CommandMessage.<CreateAggregateSub>builder()
            .metaData(CommandMetaData.builder()
                .build())
            .payload(command)
            .build();

        StepVerifier.create(commandBus.publish(commandMessage))
            .expectNextCount(1)
            .verifyComplete();
        assertThat(command.dispatched).isTrue();
    }

    @Test
    void shouldFailIfHandlerIsNotConstructorAndAggregateIdIsMissing() {
        ModifyAggregate command = new ModifyAggregate();
        ImmutableCommandMessage<ModifyAggregate> commandMessage = CommandMessage.<ModifyAggregate>builder()
            .metaData(CommandMetaData.builder()
                .build())
            .payload(command)
            .build();

        StepVerifier.create(commandBus.publish(commandMessage))
            .verifyErrorMessage("Missing aggregate ID from command " + commandMessage);
    }

    @Test
    void shouldCallHandlerMethod() {
        Aggregate1 aggregate = new Aggregate1();
        when(aggregateLoader.load(AN_AGGREGATE_ID)).thenReturn(Mono.just(aggregate));

        ModifyAggregate command = new ModifyAggregate();
        ImmutableCommandMessage<ModifyAggregate> commandMessage = CommandMessage.<ModifyAggregate>builder()
            .metaData(CommandMetaData.builder()
                .aggregateId(AN_AGGREGATE_ID)
                .build())
            .payload(command)
            .build();

        StepVerifier.create(commandBus.publish(commandMessage))
            .expectNextCount(1)
            .verifyComplete();
        assertThat(command.dispatched).isTrue();
        assertThat(aggregate.dispatched).isTrue();
    }

    @Test
    void shouldEmitEventViaContext() {
        Aggregate1 aggregate = new Aggregate1();
        when(aggregateLoader.load(AN_AGGREGATE_ID)).thenReturn(Mono.just(aggregate));

        TestEventEmitting command = new TestEventEmitting();
        ImmutableCommandMessage<TestEventEmitting> commandMessage = CommandMessage.<TestEventEmitting>builder()
            .metaData(CommandMetaData.builder()
                .aggregateId(AN_AGGREGATE_ID)
                .build())
            .payload(command)
            .build();

        StepVerifier.create(commandBus.publish(commandMessage))
            .expectNextCount(1)
            .verifyComplete();
    }

    private static final class Aggregate1 {

        boolean dispatched;

        private Aggregate1() {
        }

        @CommandHandler
        public Aggregate1(CreateAggregate command, CommandContext context) {
            command.dispatched = true;
            context.registerAggregateId(Aggregate1.class, "123");
        }

        @CommandHandler
        public void handle(ModifyAggregate command, CommandContext context) {
            command.dispatched = true;
            dispatched = true;
        }

        @CommandHandler
        public void handle(TestEventEmitting command, CommandContext commandContext) {
            commandContext.apply(new Event1());
        }
    }

    private static class CreateAggregate {
        boolean dispatched;
    }

    private static class CreateAggregateSub extends CreateAggregate {
    }

    private static class ModifyAggregate {
        boolean dispatched;
    }

    private static class TestEventEmitting {
    }

    private static class Event1 {
    }
}
