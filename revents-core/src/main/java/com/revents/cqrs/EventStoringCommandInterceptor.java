package com.revents.cqrs;

import static com.revents.log.TransformContextToMdc.monoLogOnNext;
import static java.util.Objects.requireNonNull;

import com.revents.CommandResult;
import com.revents.CommandMessage;
import com.revents.EventStore;
import com.revents.ImmutableCommandResult;
import com.revents.MessageInterceptor;
import com.revents.MessageInterceptorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class EventStoringCommandInterceptor implements MessageInterceptor<CommandMessage<?>, CommandResult> {

    private static final Logger LOG = LoggerFactory.getLogger(EventStoringCommandInterceptor.class);

    private final EventStore eventStore;

    public EventStoringCommandInterceptor(EventStore eventStore) {
        this.eventStore = requireNonNull(eventStore);
    }

    @Override
    public Mono<CommandResult> handle(CommandMessage<?> commandMessage,
                                       MessageInterceptorChain<CommandMessage<?>, CommandResult> chain) {
        return chain.handle(commandMessage)
            .transform(monoLogOnNext(ctx -> LOG.debug("Start storing events applied by the command handler...")))
            .flatMap(commandResult -> eventStore.store(
                    commandResult.aggregateId(),
                    commandResult.eventMessages(),
                    commandMessage.metaData().revision())
                .transform(monoLogOnNext(x -> LOG.debug("All events applied by the command handler have been stored")))
                .map(persistedEvents -> ImmutableCommandResult.copyOf(commandResult)
                    .withEventMessages(persistedEvents)));
    }
}
