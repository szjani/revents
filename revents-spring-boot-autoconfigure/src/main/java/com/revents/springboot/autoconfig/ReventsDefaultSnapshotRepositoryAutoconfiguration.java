package com.revents.springboot.autoconfig;

import com.revents.eventsourcing.InMemorySnapshotRepository;
import com.revents.eventsourcing.SnapshotRepository;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureAfter(ReventsSpringR2dbcAutoconfigure.class)
public class ReventsDefaultSnapshotRepositoryAutoconfiguration {

    @Bean
    @ConditionalOnMissingBean
    SnapshotRepository snapshotRepository() {
        return new InMemorySnapshotRepository();
    }
}
