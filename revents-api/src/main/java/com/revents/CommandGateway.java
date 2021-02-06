package com.revents;

import reactor.core.publisher.Mono;

public interface CommandGateway {

    <C> Mono<Void> send(C command);

    <C> Mono<CommandResult> sendWithResult(C command);

    <C> Mono<Void> sendAndForget(C command);
}
