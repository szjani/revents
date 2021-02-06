package com.revents;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.revents.eventstore.MongoTestDatabaseConfiguration;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

@SpringBootTest(classes = MongoTestDatabaseConfiguration.class)
class MongoTransactionalMessageInterceptorIT {

    private static final String COLLECTION = "collection";
    public static final String ID_FIELD = "_id";

    @Autowired
    MongoClient mongoClient;

    MongoDatabase mongoDatabase;

    MongoTransactionalMessageInterceptor<EventMessage<?>, String> transactionalInterceptor;

    @BeforeEach
    void setUp() {
        Hooks.onOperatorDebug();
        transactionalInterceptor = new MongoTransactionalMessageInterceptor<>(mongoClient);
        mongoDatabase = mongoClient.getDatabase("test");
        Mono.from(mongoDatabase.createCollection(COLLECTION)).block();
    }

    @AfterEach
    void tearDown() {
        Mono.from(mongoDatabase.getCollection(COLLECTION).drop()).block();
    }

    @Test
    void shouldReturnResult() {
        createChain(message -> Mono.just(message.payload().toString()))
            .handle(createEventMessage("Hello"))
            .as(StepVerifier::create)
            .expectNext("Hello")
            .verifyComplete();
    }

    @Test
    void shouldStoreTwoDocuments() {
        MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION);

        createChain(message -> Mono.deferContextual(contextView ->
            Mono.from(collection.insertOne(contextView.get(ClientSession.class), new Document(ID_FIELD, "1")))
                .then(Mono.from(
                    collection.insertOne(contextView.get(ClientSession.class), new Document(ID_FIELD, "2"))))
                .thenReturn("done")))
            .handle(createEventMessage("any"))
            .as(StepVerifier::create)
            .expectNext("done")
            .verifyComplete();

        Flux.from(collection.find())
            .as(StepVerifier::create)
            .consumeNextWith(doc -> assertThat(doc.get(ID_FIELD)).isEqualTo("1"))
            .consumeNextWith(doc -> assertThat(doc.get(ID_FIELD)).isEqualTo("2"))
            .verifyComplete();
    }

    @Test
    void shouldStoreNothingInCaseOfError() {
        MongoCollection<Document> collection = mongoDatabase.getCollection(COLLECTION);

        createChain(message -> Mono.deferContextual(contextView ->
            Mono.from(collection.insertOne(contextView.get(ClientSession.class), new Document(ID_FIELD, "1")))
                .then(Mono.error(() -> new RuntimeException("expected exception")))
                .thenReturn("")))
            .handle(createEventMessage("any"))
            .as(StepVerifier::create)
            .expectErrorMessage("expected exception")
            .verify();

        Flux.from(collection.find())
            .as(StepVerifier::create)
            .expectNextCount(0)
            .verifyComplete();
    }

    private EventMessage<String> createEventMessage(String payload) {
        return EventMessage.<String>builder()
            .metaData(EventMessage.EventMetaData.builder()
                .aggregateId(AggregateId.of(TestAggregate1.class, "1"))
                .build())
            .payload(payload)
            .build();
    }

    private MessageInterceptorChain<EventMessage<?>, String> createChain(
        MessageHandler<EventMessage<?>, String> handler) {
        return MessageInterceptorChain.create(List.of(transactionalInterceptor), handler);
    }
}
