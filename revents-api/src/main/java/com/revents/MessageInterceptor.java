package com.revents;

import reactor.core.publisher.Mono;

import java.util.function.BiFunction;

/**
 * Contract for interception-style, chained processing of messages that may
 * be used to implement cross-cutting, application-agnostic requirements such
 * as security, validation, logging, and others.
 *
 * @param <M> type of the handled message
 * @param <R> type of the result
 */
@FunctionalInterface
public interface MessageInterceptor<M extends Message<?, ?, ?>, R>
    extends BiFunction<M, MessageInterceptorChain<M, R>, Mono<R>> {

    /**
     * Process the message and (optionally) delegate to the next
     * {@link MessageInterceptor} through the given {@link MessageInterceptorChain}.
     *
     * @param message the current message
     * @param chain provides a way to delegate to the next interceptor
     * @return the result of the message processing
     */
    Mono<R> handle(M message, MessageInterceptorChain<M, R> chain);

    @Override
    default Mono<R> apply(M message, MessageInterceptorChain<M, R> chain) {
        return handle(message, chain);
    }
}
