package com.revents;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.revents.CommandMessage.CommandMetaData;
import com.revents.EventMessage.EventMetaData;
import org.immutables.value.Value;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Value.Immutable
@Value.Modifiable
@SuppressWarnings("PMD.TooManyMethods")
public interface CommandContext {

    static ImmutableCommandContext.Builder builder() {
        return ImmutableCommandContext.builder();
    }

    /**
     * Create a context for the given {@code commandMessage}
     * to pass that to the command handler.
     *
     * @param commandMessage the message being processed
     * @param <P> type of the payload
     * @return a new context
     */
    static <P> Mono<CommandContext> createFor(CommandMessage<P> commandMessage) {
        return Mono.deferContextual(context -> Mono.just(context.get(Clock.class)))
            .map(clock -> {
                ModifiableCommandContext context = ModifiableCommandContext.create();
                context.setClock(clock);
                context.setAggregateId(commandMessage.metaData().aggregateId());
                context.setMetaData(commandMessage.metaData());
                return context;
            })
            .as(ReventsClock.monoContextualClock())
            .cast(CommandContext.class);
    }

    Optional<AggregateId<?>> aggregateId();

    Clock clock();

    CommandMetaData metaData();

    List<EventMessage<?>> appliedEventMessages();

    List<Object> furtherCommands();

    /**
     * Apply an event from the command handler.
     *
     * @param event the event
     * @param <T> type of the event
     * @return this instance
     */
    @CanIgnoreReturnValue
    default <T> CommandContext apply(T event) {
        if (this instanceof ModifiableCommandContext) {
            ((ModifiableCommandContext) this).addAppliedEventMessages(EventMessage.builder()
                .metaData(EventMetaData.builder()
                    .withClock(clock())
                    .aggregateId(aggregateId()
                        .orElseThrow(() ->
                            new RuntimeException("Aggregate ID has not been set into the command context")))
                    .build())
                .payload(event)
                .build());
            return this;
        } else {
            throw new UnsupportedOperationException("This command context cannot be modified anymore!");
        }
    }

    /**
     * Request an additional command processing.
     *
     * <p>
     *     The given command will be processed in a completely separated flow,
     *     which means each command message interceptor will be executed again.
     * </p>
     * <p>
     *     Simplify triggering a new command, instead of emitting an event,
     *     handling that in an event handler and sending {@code command} to the {@link CommandGateway}.
     * </p>
     *
     * @param command the new command
     * @param <T> type of the command
     * @return this context
     */
    default <T> CommandContext request(T command) {
        if (this instanceof ModifiableCommandContext) {
            ((ModifiableCommandContext) this).addFurtherCommands(command);
            return this;
        } else {
            throw new UnsupportedOperationException("This command context cannot be modified anymore!");
        }
    }

    /**
     * Register a new aggregate instance.
     *
     * <p>
     *     Usually called from aggregate root constructor command handlers,
     *     if the aggregate ID is not known until that time
     * </p>
     *
     * @param aggregateClass class of the aggregate root
     * @param id ID of the aggregate
     * @param <T> type of the aggregate root
     * @return this context
     */
    @CanIgnoreReturnValue
    default <T> CommandContext registerAggregateId(Class<T> aggregateClass, String id) {
        if (this instanceof ModifiableCommandContext) {
            if (aggregateId().isPresent()) {
                throw new UnsupportedOperationException(
                    "Aggregate ID is already known by the system " + aggregateClass + ", " + id);
            }
            ((ModifiableCommandContext) this).setAggregateId(AggregateId.of(aggregateClass, id));
            return this;
        } else {
            throw new UnsupportedOperationException("This command context cannot be modified anymore!");
        }
    }

    @CanIgnoreReturnValue
    default CommandContext registerAggregateId(Object aggregate, String id) {
        return registerAggregateId(aggregate.getClass(), id);
    }
}
