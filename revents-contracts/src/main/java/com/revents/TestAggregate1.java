package com.revents;

import com.google.common.base.Preconditions;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

@Aggregate
public final class TestAggregate1 {

    String id;
    int commandPayload;

    TestAggregate1() {
    }

    /**
     * Create an aggregate instance only to simulate snapshot loading.
     *
     * @param commandPayload the expected payload
     * @return a new aggregate instance
     */
    public static TestAggregate1 createForSnapshotTest(int commandPayload) {
        TestAggregate1 aggregate = new TestAggregate1();
        aggregate.id = UUID.randomUUID().toString();
        aggregate.commandPayload = commandPayload;
        return aggregate;
    }

    public String getId() {
        return id;
    }

    public int getCommandPayload() {
        return commandPayload;
    }

    /**
     * Handles the {@link TestCommand1} command.
     *
     * @param command the command
     * @param context the command context
     */
    @CommandHandler
    public TestAggregate1(TestCommand1 command, CommandContext context) {
        Preconditions.checkArgument(command.payload() != 42, "Payload must not be 42");
        context
            .registerAggregateId(this, command.useThisId())
            .apply(ImmutableTestEvent1.builder()
                .aggregateId(command.useThisId())
                .payload(command.payload())
                .build());
    }

    /**
     * Handles the {@link TestCommand2} command.
     *
     * @param command the command
     * @param context the command context
     */
    @CommandHandler
    public void handle(TestCommand2 command, CommandContext context) {
        if (commandPayload % command.payload() != 0) {
            throw new IllegalStateException(commandPayload + " cannot be divided by " + command.payload());
        }
        context.apply(ImmutableTestEvent2.builder()
            .payload(command.payload())
            .build());
    }

    /**
     * Handles the {@link TestCommand3} command.
     *
     * @param command the command
     * @param context the command context
     */
    @CommandHandler
    public void handle(TestCommand3 command, CommandContext context) {
        context.apply(ImmutableTestEvent3.builder()
            .created(ZonedDateTime.now(context.clock()))
            .payload(command.payload())
            .build());
    }

    @EventHandler
    private void on(TestEvent1 event) {
        id = event.aggregateId();
        commandPayload = event.payload();
    }

    @EventHandler
    private void on(TestEvent2 event) {
        commandPayload = event.payload();
    }

    @EventHandler
    private void on(TestEvent3 event) {
        commandPayload = event.payload();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TestAggregate1 that = (TestAggregate1) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
