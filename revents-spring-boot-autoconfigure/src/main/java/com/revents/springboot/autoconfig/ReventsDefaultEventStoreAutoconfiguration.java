package com.revents.springboot.autoconfig;

import com.revents.EventStore;
import com.revents.eventstore.InMemoryEventStore;
import com.revents.eventstore.TokenStore;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureAfter(ReventsSpringR2dbcAutoconfigure.class)
public class ReventsDefaultEventStoreAutoconfiguration {

    @Bean
    @ConditionalOnMissingBean
    EventStore eventStore(TokenStore tokenStore) {
        return new InMemoryEventStore(tokenStore);
    }
}
