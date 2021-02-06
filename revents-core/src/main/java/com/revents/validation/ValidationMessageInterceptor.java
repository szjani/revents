package com.revents.validation;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;

import com.revents.Message;
import com.revents.MessageInterceptor;
import com.revents.MessageInterceptorChain;
import reactor.core.publisher.Mono;

import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.Set;

/**
 * Validate the {@code message} with {@code javax.validation.Validator}.
 *
 * @param <M> type of the message
 * @param <R> type of the result
 */
public class ValidationMessageInterceptor<M extends Message<?, ?, ?>, R> implements MessageInterceptor<M, R> {

    private final Validator validator;

    public ValidationMessageInterceptor(Validator validator) {
        this.validator = requireNonNull(validator);
    }

    @Override
    public Mono<R> handle(M message, MessageInterceptorChain<M, R> chain) {
        return Mono.just(message.payload())
            .map(validator::validate)
            .filter(not(Set::isEmpty))
            .flatMap(violations -> Mono.<R>error(new ConstraintViolationException("Message is not valid", violations)))
            .switchIfEmpty(chain.handle(message));
    }
}
