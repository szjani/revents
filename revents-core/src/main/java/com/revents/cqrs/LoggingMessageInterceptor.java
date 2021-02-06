package com.revents.cqrs;

import static com.revents.log.TransformContextToMdc.monoLogOnNext;
import static com.revents.log.TransformContextToMdc.monoLogOnSubscribe;

import com.revents.Message;
import com.revents.MessageInterceptor;
import com.revents.MessageInterceptorChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class LoggingMessageInterceptor<M extends Message<?, ?, ?>, R> implements MessageInterceptor<M, R> {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingMessageInterceptor.class);

    @Override
    public Mono<R> handle(M message, MessageInterceptorChain<M, R> chain) {
        return Mono.<R>empty()
            .transform(monoLogOnSubscribe(s -> LOG.info("Before message={}", message)))
            .switchIfEmpty(chain.handle(message))
            .transform(monoLogOnNext(result -> LOG.info("After message={}, result={}", message, result)));
    }
}
