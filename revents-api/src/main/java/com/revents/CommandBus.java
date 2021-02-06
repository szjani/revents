package com.revents;

import reactor.core.publisher.Mono;

public interface CommandBus {

    <P> Mono<CommandResult> publish(CommandMessage<P> commandMessage);
}
