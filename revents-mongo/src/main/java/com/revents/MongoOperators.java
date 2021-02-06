package com.revents;

import static com.revents.log.TransformContextToMdc.monoLogOnNext;
import static com.revents.log.TransformContextToMdc.monoLogOnSubscribe;

import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public final class MongoOperators {

    private static final Logger LOG = LoggerFactory.getLogger(MongoOperators.class);

    private MongoOperators() {
    }

    /**
     * Wraps the given {@code mono} into a new MongoDB {@link ClientSession} scope.
     *
     * <p>
     *     Check if a {@link ClientSession} exists in the reactor context. If that
     *     cannot be found then it creates a new one, which will be closed eventually.
     * </p>
     *
     * @param mongoClient to create the session
     * @param <T> the emitted type
     * @return a new function, that can be applied {@code mono.as(wrapInSession(client))}
     */
    public static <T> Function<Mono<T>, Mono<T>> wrapInSession(MongoClient mongoClient) {
        return mono -> Mono.deferContextual(inheritedContext ->
            Mono.justOrEmpty(inheritedContext.<ClientSession>getOrEmpty(ClientSession.class))
                .switchIfEmpty(Mono.defer(() -> Mono.from(mongoClient.startSession())
                    .transform(monoLogOnNext(clientSession ->
                        LOG.debug("Starting new Mongo clientSession={}", clientSession)))))
                .flatMap(clientSession -> mono.contextWrite(ctx -> ctx.put(ClientSession.class, clientSession))
                    .doFinally(x -> {
                        if (inheritedContext.<ClientSession>getOrEmpty(ClientSession.class).isEmpty()) {
                            clientSession.close();
                        }
                    })));
    }

    /**
     * Wraps the given {@code mono} into a new MongoDB transaction.
     *
     * <p>
     *     The {@link ClientSession} must be available in the reactor context!
     * </p>
     *
     * <p>
     *     If a transaction is already started then leave our {@code mono} as is.
     *     Otherwise create a new transaction, commit/abbort as is expected.
     * </p>
     *
     * <p>
     *     All wrapped mongo statement must use the {@code ClientSession} from the reactor context!
     * </p>
     *
     * @param mongoClient to create the transaction
     * @param <T> the emitted type
     * @return a new function, that can be applied {@code mono.as(wrapInTransaction(client))}
     */
    public static <T> Function<Mono<T>, Mono<T>> wrapInTransaction(MongoClient mongoClient) {
        return mono -> Mono.deferContextual(contextView -> Mono.just(contextView.get(ClientSession.class)))
            .flatMap(clientSession -> clientSession.hasActiveTransaction()
                ? mono
                : Mono.fromRunnable(clientSession::startTransaction)
                    .transform(monoLogOnSubscribe(s -> LOG.debug("Mongo transaction started.")))
                    .then(mono)
                    .onErrorResume(e -> Mono.from(clientSession.abortTransaction())
                        .transform(monoLogOnSubscribe(s -> LOG.debug("Mongo transaction aborted.")))
                        .then(Mono.error(e)))
                    .flatMap(result -> Mono.from(clientSession.commitTransaction())
                        .transform(monoLogOnSubscribe(s -> LOG.debug("Mongo transaction committed.")))
                        .thenReturn(result))
                    .switchIfEmpty(Mono.defer(() -> Mono.from(clientSession.commitTransaction())
                        .transform(monoLogOnSubscribe(s -> LOG.debug("Mongo transaction committed.")))
                        .then(Mono.empty()))));
    }
}
