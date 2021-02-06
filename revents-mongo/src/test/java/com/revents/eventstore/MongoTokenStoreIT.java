package com.revents.eventstore;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = MongoTestDatabaseConfiguration.class)
class MongoTokenStoreIT extends TokenStoreContract {

    @Autowired
    MongoDatabase mongoDatabase;

    @Autowired
    MongoClient mongoClient;

    @Override
    protected TokenStore createTokenStore() {
        return new MongoTokenStore(mongoDatabase, mongoClient);
    }

    @BeforeEach
    void dropAllData() {
        Mono.from(mongoDatabase.getCollection("tokens").drop()).block();
    }
}
