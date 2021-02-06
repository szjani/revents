package com.revents;

import static java.util.Objects.requireNonNull;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.ListIterator;

/**
 * Contract to allow a {@link MessageInterceptor} to delegate to the next in the chain.
 *
 * @param <M> type of the handled message
 * @param <R> type of the result
 */
@FunctionalInterface
public interface MessageInterceptorChain<M extends Message<?, ?, ?>, R> extends MessageHandler<M, R> {

    static <M extends Message<?, ?, ?>, R> MessageInterceptorChain<M, R> create(
        List<MessageInterceptor<M, R>> interceptors, MessageHandler<? super M, ? extends R> messageHandler) {

        return new DefaultMessageInterceptorChain<>(interceptors, messageHandler);
    }

    final class DefaultMessageInterceptorChain<M extends Message<?, ?, ?>, R> implements MessageInterceptorChain<M, R> {

        private final MessageInterceptor<M, R> currentInterceptor;
        private final MessageInterceptorChain<M, R> chain;
        private final MessageHandler<? super M, ? extends R> messageHandler;

        @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
        private DefaultMessageInterceptorChain(List<MessageInterceptor<M, R>> interceptors,
                                               MessageHandler<? super M, ? extends R> messageHandler) {
            DefaultMessageInterceptorChain<M, R> beginning = new DefaultMessageInterceptorChain<>(
                null, null, messageHandler);
            ListIterator<MessageInterceptor<M, R>> iterator = interceptors.listIterator(interceptors.size());
            while (iterator.hasPrevious()) {
                beginning = new DefaultMessageInterceptorChain<>(iterator.previous(), beginning, messageHandler);
            }
            currentInterceptor = beginning.currentInterceptor;
            chain = beginning.chain;
            this.messageHandler = beginning.messageHandler;
        }

        private DefaultMessageInterceptorChain(MessageInterceptor<M, R> currentInterceptor,
                                               MessageInterceptorChain<M, R> chain,
                                               MessageHandler<? super M, ? extends R> messageHandler) {
            this.currentInterceptor = currentInterceptor;
            this.chain = chain;
            this.messageHandler = requireNonNull(messageHandler);
        }

        @Override
        public Mono<R> handle(M message) {
            return Mono.defer(() -> currentInterceptor != null && chain != null
                ? currentInterceptor.handle(message, chain)
                : messageHandler.handle(message));
        }
    }
}
