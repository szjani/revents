package com.revents.springboot.autoconfig;

import com.revents.EventProcessor.EventProcessorId;
import com.revents.eventstore.InMemoryTokenStore;
import com.revents.eventstore.TokenStore;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

@Configuration
@AutoConfigureAfter(ReventsMongoTokenStoreAutoconfiguration.class)
public class ReventsDefaultTokenStoreAutoconfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "tokenStoreForConsumerId")
    Function<EventProcessorId, TokenStore> tokenStoreForConsumerId(TokenStore tokenStore) {
        return x -> tokenStore;
    }

    @Bean
    @ConditionalOnMissingBean
    TokenStore tokenStore() {
        return new InMemoryTokenStore();
    }
}
