package com.revents.eventstore;

import com.revents.R2dbcTestDatabaseConfiguration;
import com.revents.EventStore;
import com.revents.EventStoreContract;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

import java.time.Duration;

@SpringBootTest(classes = R2dbcTestDatabaseConfiguration.class)
class SpringR2dbcEventStoreIT extends EventStoreContract {

    @Autowired
    DatabaseClient databaseClient;

    @Autowired
    ReactiveTransactionManager reactiveTransactionManager;

    @BeforeEach
    void dropAllData() {
        databaseClient.sql("DELETE FROM stream_link").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM event").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM aggregate").fetch().rowsUpdated().block();
    }

    @Override
    protected EventStore createEventStore() {
        return SpringR2dbcEventStore.create(config -> config
            .databaseClient(databaseClient)
            .reactiveTransactionManager(reactiveTransactionManager)
            .eventStreamFetchRepeat(Duration.ofSeconds(1))
            .tokenStore(new InMemoryTokenStore()));
    }
}
