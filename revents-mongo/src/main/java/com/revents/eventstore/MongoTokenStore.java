package com.revents.eventstore;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.currentDate;
import static com.mongodb.client.model.Updates.set;
import static com.revents.MongoOperators.wrapInSession;
import static java.util.Objects.requireNonNull;

import com.mongodb.WriteConcern;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.revents.EventMessage;
import com.revents.EventProcessor.EventProcessorId;
import com.revents.EventStreamPosition;
import com.revents.Message;
import reactor.core.publisher.Mono;

public class MongoTokenStore implements TokenStore {

    private final MongoDatabase mongoDatabase;
    private final MongoClient mongoClient;

    public MongoTokenStore(MongoDatabase mongoDatabase, MongoClient mongoClient) {
        this.mongoDatabase = requireNonNull(mongoDatabase);
        this.mongoClient = requireNonNull(mongoClient);
    }

    @Override
    public Mono<EventStreamPosition> tokenFor(EventProcessorId processorId) {
        return Mono.from(mongoDatabase.getCollection("tokens")
            .find(eq("_id", processorId.name()))
            .first())
            .map(document -> document.get("token", String.class))
            .map(EventMessage.EventId::of)
            .map(EventStreamPosition::of)
            .defaultIfEmpty(EventStreamPosition.BEGINNING);
    }

    @Override
    public Mono<Void> save(EventProcessorId processorId, EventStreamPosition token) {
        return Mono.deferContextual(contextView -> Mono.just(contextView.get(ClientSession.class)))
            .flatMap(clientSession -> Mono.justOrEmpty(token.lastProcessedEvent())
                .map(Message.MessageId::rawId)
                .flatMap(tokenId -> Mono.from(mongoDatabase
                    .getCollection("tokens")
                    .withWriteConcern(WriteConcern.MAJORITY)
                    .updateOne(clientSession,
                        eq("_id", processorId.name()),
                        combine(set("token", tokenId), currentDate("lastModified")),
                        new UpdateOptions().upsert(true))))
                .then())
            .as(wrapInSession(mongoClient));
    }
}
