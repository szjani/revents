package com.revents.springboot.autoconfig;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.revents.eventstore.MongoTokenStore;
import com.revents.eventstore.TokenStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({MongoDatabase.class, MongoClient.class})
@EnableConfigurationProperties(ReventsSpringProperties.class)
public class ReventsMongoTokenStoreAutoconfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "revents.mongo.token-store.enabled", havingValue = "true")
    TokenStore tokenStore(MongoDatabase mongoDatabase, MongoClient mongoClient) {
        return new MongoTokenStore(mongoDatabase, mongoClient);
    }
}
