package com.revents;

import static com.revents.MongoOperators.wrapInSession;
import static com.revents.MongoOperators.wrapInTransaction;
import static java.util.Objects.requireNonNull;

import com.mongodb.reactivestreams.client.MongoClient;
import reactor.core.publisher.Mono;

public class MongoTransactionalMessageInterceptor<M extends Message<?, ?, ?>, R> implements MessageInterceptor<M, R> {

    private final MongoClient mongoClient;

    public MongoTransactionalMessageInterceptor(MongoClient mongoClient) {
        this.mongoClient = requireNonNull(mongoClient);
    }

    @Override
    public Mono<R> handle(M message, MessageInterceptorChain<M, R> chain) {
        return chain.handle(message)
            .as(wrapInTransaction(mongoClient))
            .as(wrapInSession(mongoClient));
    }
}
