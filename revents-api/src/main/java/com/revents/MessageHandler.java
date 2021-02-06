package com.revents;

import reactor.core.publisher.Mono;

import java.util.function.Function;

@FunctionalInterface
public interface MessageHandler<M extends Message<?, ?, ?>, R> extends Function<M, Mono<R>> {

    Mono<R> handle(M message);

    @Override
    default Mono<R> apply(M message) {
        return handle(message);
    }
}
