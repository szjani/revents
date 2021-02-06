package com.revents;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.revents.eventstore.InMemoryTokenStore;
import com.revents.eventstore.MongoTokenStore;
import com.revents.eventstore.TokenStore;
import com.revents.springboot.autoconfig.ReventsDefaultCommandBusAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultEventStoreAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultSerializerAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultSnapshotRepositoryAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultTokenStoreAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsMongoTokenStoreAutoconfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
public class ReventsSpringMongoAutoconfigurationIT {

    @Mock
    MongoClient mongoClient;

    @Mock
    MongoDatabase mongoDatabase;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withPropertyValues("revents.scanPackage=com.revents")
        .withConfiguration(AutoConfigurations.of(
            ReventsDefaultCommandBusAutoconfiguration.class,
            ReventsDefaultEventStoreAutoconfiguration.class,
            ReventsDefaultSerializerAutoconfiguration.class,
            ReventsDefaultSnapshotRepositoryAutoconfiguration.class,
            ReventsDefaultTokenStoreAutoconfiguration.class,
            ReventsMongoTokenStoreAutoconfiguration.class));

    @Test
    void shouldCreateMongoTokenStore() {
        contextRunner
            .withBean(null, MongoClient.class, () -> mongoClient)
            .withBean(null, MongoDatabase.class, () -> mongoDatabase)
            .withPropertyValues("revents.mongo.token-store.enabled=true")
            .run(context -> assertThat(context)
                .hasSingleBean(TokenStore.class)
                .hasBean("tokenStoreForConsumerId")
                .getBean(TokenStore.class).isInstanceOf(MongoTokenStore.class));
    }

    @Test
    void shouldFallbackIfMongoDatabaseClassIsNotAvailable() {
        contextRunner
            .withClassLoader(new FilteredClassLoader(MongoDatabase.class))
            .withPropertyValues("revents.mongo.token-store.enabled=true")
            .run(context -> assertThat(context)
                .hasSingleBean(TokenStore.class)
                .hasBean("tokenStoreForConsumerId")
                .getBean(TokenStore.class).isInstanceOf(InMemoryTokenStore.class));
    }
}
