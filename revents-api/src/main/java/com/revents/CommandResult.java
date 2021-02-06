package com.revents;

import com.google.common.collect.ImmutableList;
import org.immutables.value.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Value.Immutable
public interface CommandResult {

    AggregateId<?> aggregateId();

    List<Object> furtherCommands();

    List<CommandResult> furtherCommandResults();

    List<EventMessage<?>> eventMessages();

    /**
     * Collect all the event messages from this and each {@code furtherCommandResults()} recursively.
     *
     * @return all event messages
     */
    default List<EventMessage<?>> allEventMessages() {
        return ImmutableList.<EventMessage<?>>builder()
            .addAll(eventMessages())
            .addAll(furtherCommandResults().stream()
                .flatMap(commandResult -> commandResult.allEventMessages().stream())
                .collect(Collectors.toList()))
            .build();
    }

    /**
     * Helper method to find the {@code clazz} type of events.
     *
     * <p>Useful in case of {@link CommandGateway#sendWithResult(Object)}</p>
     *
     * @param clazz type of the events we need
     * @param <T> event type
     * @return the events
     */
    default <T> Flux<T> eventsOf(Class<T> clazz) {
        return Flux.fromIterable(eventMessages())
            .map(EventMessage::payload)
            .filter(clazz::isInstance)
            .cast(clazz)
            .concatWith(Flux.fromIterable(furtherCommandResults())
                .flatMap(furtherCommandResult -> furtherCommandResult.eventsOf(clazz)));
    }

    /**
     * The same as {@link CommandResult#eventsOf(Class)} but returns
     * only the last event.
     *
     * @param clazz type of the event we need
     * @param <T> event type
     * @return the event
     */
    default <T> Mono<T> eventOf(Class<T> clazz) {
        return eventsOf(clazz).last();
    }
}
