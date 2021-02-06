package com.revents.springboot.autoconfig;

import com.revents.serialization.Serializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ReventsDefaultSerializerAutoconfiguration {

    @Bean
    @Qualifier("snapshotSerializer")
    @ConditionalOnMissingBean(name = "snapshotSerializer")
    Serializer snapshotSerializer() {
        return Serializer.defaultJackson();
    }

    @Bean
    @Qualifier("eventSerializer")
    @ConditionalOnMissingBean(name = "eventSerializer")
    Serializer eventSerializer() {
        return Serializer.defaultJackson();
    }
}
