package com.revents.cqrs;

import static com.revents.log.TransformContextToMdc.monoLogOnError;

import com.revents.CommandBus;
import com.revents.CommandGateway;
import com.revents.CommandMessage;
import com.revents.CommandMessage.CommandMetaData;
import com.revents.CommandResult;
import com.revents.ImmutableCommandResult;
import com.revents.ReventsClock;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.UnaryOperator;

public class LocalCommandGateway implements CommandGateway {

    private static final Logger LOG = LoggerFactory.getLogger(LocalCommandGateway.class);

    private final CommandBus commandBus;
    private final CommandAggregateIdResolver commandAggregateIdResolver;
    private final CommandRevisionResolver commandRevisionResolver;

    private LocalCommandGateway(LocalCommandGatewayConfig config) {
        commandBus = config.commandBus();
        commandAggregateIdResolver = config.commandAggregateIdResolver();
        commandRevisionResolver = config.commandRevisionResolver();
    }

    public static LocalCommandGateway create(UnaryOperator<ImmutableLocalCommandGatewayConfig.Builder> init) {
        return new LocalCommandGateway(init.apply(ImmutableLocalCommandGatewayConfig.builder()).build());
    }

    @Override
    public <C> Mono<Void> send(C command) {
        return sendWithResult(command).then();
    }

    @Override
    public <C> Mono<Void> sendAndForget(C command) {
        return Mono.fromRunnable(() -> send(command).subscribe());
    }

    @Override
    public <C> Mono<CommandResult> sendWithResult(C command) {
        return Mono.deferContextual(context -> Mono.just(context.get(Clock.class)))
            .flatMap(clock -> commandAggregateIdResolver.aggregateIdOf(command)
                .map(Optional::of)
                .switchIfEmpty(Mono.just(Optional.empty()))
                .flatMap(aggregateId -> commandRevisionResolver.revisionOf(command)
                    .map(OptionalLong::of)
                    .switchIfEmpty(Mono.just(OptionalLong.empty()))
                    .map(revision -> CommandMessage.builder()
                        .payload(command)
                        .metaData(CommandMetaData.builder()
                            .aggregateId(aggregateId)
                            .revision(revision)
                            .withClock(clock)
                            .build())
                        .build()))
                .flatMap(commandBus::publish)
                .transform(monoLogOnError(e -> LOG.debug("Error during command handling", e))))
            .flatMap(commandResult -> Flux.fromIterable(commandResult.furtherCommands())
                .flatMap(this::sendWithResult)
                .collectList()
                .map(furtherCommandResults -> ImmutableCommandResult.copyOf(commandResult)
                    .withFurtherCommandResults(furtherCommandResults)))
            .cast(CommandResult.class)
            .as(ReventsClock.monoContextualClock());
    }

    @Value.Immutable
    public interface LocalCommandGatewayConfig {

        CommandBus commandBus();

        CommandAggregateIdResolver commandAggregateIdResolver();

        @Value.Default
        default CommandRevisionResolver commandRevisionResolver() {
            return CommandRevisionResolver.ANNOTATION_BASED;
        }
    }
}
