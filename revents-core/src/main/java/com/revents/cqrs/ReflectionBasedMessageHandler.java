package com.revents.cqrs;

import static com.revents.log.TransformContextToMdc.monoLogOnSuccess;

import com.google.common.base.Preconditions;
import com.revents.AggregateLoader;
import com.revents.CommandContext;
import com.revents.CommandMessage;
import com.revents.CommandResult;
import com.revents.EventContext;
import com.revents.EventMessage;
import com.revents.ImmutableCommandResult;
import com.revents.Message;
import com.revents.MessageHandler;
import com.revents.NotFollowedReventsConventionException;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

public interface ReflectionBasedMessageHandler<M extends Message<?, ?, ?>, C> extends MessageHandler<M, C> {

    Logger LOG = LoggerFactory.getLogger(ReflectionBasedMessageHandler.class);

    Executable handlerExecutable();

    Optional<Object> handlerObject();

    default boolean canHandle(Object payload) {
        return payloadType().isInstance(payload);
    }

    default Class<?> payloadType() {
        return handlerExecutable().getParameterTypes()[0];
    }

    @Value.Immutable
    interface EventHandlerWrapper extends ReflectionBasedMessageHandler<EventMessage<?>, Void> {

        @Value.Check
        default void check() {
            handlerExecutable().setAccessible(true);
            Preconditions.checkState(handlerExecutable() instanceof Method);
            Preconditions.checkState(handlerExecutable().getParameterCount() == 2,
                "Missing EventContext parameter from handler method");
            Preconditions.checkState(((Method) handlerExecutable()).getReturnType().equals(Mono.class));
            Preconditions.checkState(handlerExecutable().getParameterTypes()[1].isAssignableFrom(EventContext.class));
            Preconditions.checkState(handlerObject().isPresent());
            Preconditions.checkState(handlerExecutable().canAccess(handlerObject().get()));
        }

        @SuppressWarnings("unchecked")
        @Override
        default Mono<Void> handle(EventMessage<?> message) {
            return EventContext.createFor(message)
                .flatMap(eventContext -> Mono.just(handlerExecutable())
                    .cast(Method.class)
                    .flatMap(method -> {
                        try {
                            return (Mono<Void>) method.invoke(handlerObject().get(), message.payload(), eventContext);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new NotFollowedReventsConventionException("Cannot invoke event handler " + method, e);
                        }
                    })
                    .transform(monoLogOnSuccess(x ->
                        LOG.debug("Event handler executed={} with message={}", this, message))));
        }
    }

    @Value.Immutable
    interface AggregateCommandHandlerWrapper extends ReflectionBasedMessageHandler<CommandMessage<?>, CommandResult> {

        AggregateLoader aggregateLoader();

        @Value.Check
        default void check() {
            handlerExecutable().setAccessible(true);
            Preconditions.checkState(handlerObject().isEmpty());
            Preconditions.checkState(handlerExecutable().getParameterCount() == 2);
            Preconditions.checkState(handlerExecutable().getParameterTypes()[1].isAssignableFrom(CommandContext.class));
            if (handlerExecutable() instanceof Method) {
                Preconditions.checkState(((Method) handlerExecutable()).getReturnType().equals(void.class));
            }
        }

        @Override
        default Mono<CommandResult> handle(CommandMessage<?> message) {
            return CommandContext.createFor(message)
                .flatMap(commandContext -> Mono.just(handlerExecutable())
                    .flatMap(executable -> Mono.just(executable)
                        .filter(Constructor.class::isInstance)
                        .cast(Constructor.class)
                        .flatMap(constructor -> Mono.fromCallable(() ->
                            constructor.newInstance(message.payload(), commandContext)))
                        .onErrorMap(InvocationTargetException.class, InvocationTargetException::getCause)
                        .switchIfEmpty(Mono.just(executable)
                            .filter(Method.class::isInstance)
                            .cast(Method.class)
                            .flatMap(method -> Mono.justOrEmpty(message.metaData().aggregateId())
                                .switchIfEmpty(Mono.error(() ->
                                    new RuntimeException("Missing aggregate ID from command " + message)))
                                .flatMap(aggregateId -> aggregateLoader().load(aggregateId))
                                .flatMap(aggregate -> Mono.fromCallable(() ->
                                        method.invoke(aggregate, message.payload(), commandContext))
                                    .onErrorMap(InvocationTargetException.class, InvocationTargetException::getCause)
                                )))
                        .transform(monoLogOnSuccess(x ->
                            LOG.debug("Command handler executed={} with message={}", this, message))))
                    .then(Mono.fromCallable(() -> ImmutableCommandResult.builder()
                        .aggregateId(commandContext.aggregateId().orElseThrow(() ->
                            new NotFollowedReventsConventionException(
                                "Aggregate ID is not known after processing command " + message)))
                        .eventMessages(commandContext.appliedEventMessages())
                        .furtherCommands(commandContext.furtherCommands())
                        .build())));
        }
    }
}
