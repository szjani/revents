package com.revents.cqrs;

import com.revents.CommandBus;
import com.revents.CommandResult;
import com.revents.CommandMessage;
import com.revents.MessageHandler;
import com.revents.MessageInterceptor;
import com.revents.MessageInterceptorChain;
import com.revents.cqrs.AnnotationAwareCommandMessageDispatcher.AnnotationAwareCommandMessageDispatcherConfig;
import org.immutables.value.Value;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.UnaryOperator;

public class DefaultCommandBus implements CommandBus {

    private final MessageHandler<CommandMessage<?>, CommandResult> commandMessageHandler;
    private final List<MessageInterceptor<CommandMessage<?>, CommandResult>> commandMessageInterceptors;

    private DefaultCommandBus(CommandBusConfig config) {
        commandMessageHandler = config.commandMessageHandler();
        commandMessageInterceptors = config.commandMessageInterceptors();
    }

    public static DefaultCommandBus create(UnaryOperator<CommandBusConfig.Builder> init) {
        return new DefaultCommandBus(init.apply(new CommandBusConfig.Builder()).build());
    }

    @Override
    public <P> Mono<CommandResult> publish(CommandMessage<P> commandMessage) {
        return MessageInterceptorChain.create(commandMessageInterceptors, commandMessageHandler).handle(commandMessage)
            .contextWrite(context -> context.put("cid", commandMessage.metaData().id().rawId()));
    }

    @Value.Immutable
    public interface CommandBusConfig {

        MessageHandler<CommandMessage<?>, CommandResult> commandMessageHandler();

        List<MessageInterceptor<CommandMessage<?>, CommandResult>> commandMessageInterceptors();

        class Builder extends ImmutableCommandBusConfig.Builder {

            public Builder annotationBasedCommandMessageHandler(
                UnaryOperator<AnnotationAwareCommandMessageDispatcherConfig.Builder> init) {
                return commandMessageHandler(AnnotationAwareCommandMessageDispatcher.create(init));
            }
        }
    }
}
