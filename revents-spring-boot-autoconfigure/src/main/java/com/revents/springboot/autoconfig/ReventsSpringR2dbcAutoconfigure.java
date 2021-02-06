package com.revents.springboot.autoconfig;

import com.revents.EventStore;
import com.revents.eventsourcing.SnapshotRepository;
import com.revents.eventsourcing.SpringR2dbcSnapshotRepository;
import com.revents.eventstore.SpringR2dbcEventStore;
import com.revents.eventstore.TokenStore;
import com.revents.serialization.SerializedTypeMapper;
import com.revents.serialization.Serializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({DatabaseClient.class, ReactiveTransactionManager.class})
@EnableConfigurationProperties(ReventsSpringProperties.class)
public class ReventsSpringR2dbcAutoconfigure {

    @Bean
    @ConditionalOnMissingBean
    SerializedTypeMapper serializedTypeMapper(ReventsSpringProperties properties) {
        return SerializedTypeMapper.scanSerializedTypeAnnotatedClassesIn(properties.getScanPackage())
            .fallbackTo(SerializedTypeMapper.CLASS_NAME_BASED);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "revents.r2dbc.snapshot-repository.enabled", havingValue = "true")
    SnapshotRepository snapshotRepository(DatabaseClient databaseClient,
                                          ReactiveTransactionManager reactiveTransactionManager,
                                          @Qualifier("snapshotSerializer") Serializer serializer) {
        return SpringR2dbcSnapshotRepository.create(builder -> builder
            .databaseClient(databaseClient)
            .reactiveTransactionManager(reactiveTransactionManager)
            .serializer(serializer));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "revents.r2dbc.event-store.enabled", havingValue = "true")
    EventStore eventStore(
        DatabaseClient databaseClient,
        ReactiveTransactionManager reactiveTransactionManager,
        @Qualifier("eventSerializer") Serializer serializer,
        TokenStore tokenStore,
        ReventsSpringProperties properties,
        SerializedTypeMapper serializedTypeMapper) {

        return SpringR2dbcEventStore.create(builder -> builder
            .databaseClient(databaseClient)
            .reactiveTransactionManager(reactiveTransactionManager)
            .tokenStore(tokenStore)
            .serializer(serializer)
            .serializedTypeMapper(serializedTypeMapper)
            .eventStreamBucketSize(properties.getR2dbc().getEventStore().getEventStreamBucketSize())
            .eventStreamFetchRepeat(properties.getR2dbc().getEventStore().getEventStreamFetchRepeat()));
    }
}
