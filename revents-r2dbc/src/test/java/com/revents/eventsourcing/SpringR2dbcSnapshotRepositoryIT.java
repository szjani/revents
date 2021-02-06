package com.revents.eventsourcing;

import com.revents.R2dbcTestDatabaseConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

@SpringBootTest(classes = R2dbcTestDatabaseConfiguration.class)
class SpringR2dbcSnapshotRepositoryIT extends SnapshotRepositoryContract {

    @Autowired
    DatabaseClient databaseClient;

    @Autowired
    ReactiveTransactionManager reactiveTransactionManager;

    @BeforeEach
    void dropAllData() {
        databaseClient.sql("DELETE FROM snapshot").fetch().rowsUpdated().block();
    }

    @Override
    protected SnapshotRepository createSnapshotRepository() {
        return SpringR2dbcSnapshotRepository.create(config -> config
            .databaseClient(databaseClient)
            .reactiveTransactionManager(reactiveTransactionManager));
    }
}
