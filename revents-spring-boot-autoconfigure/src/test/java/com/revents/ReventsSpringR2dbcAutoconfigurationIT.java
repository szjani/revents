package com.revents;

import static org.assertj.core.api.Assertions.assertThat;

import com.revents.cqrs.DefaultCommandBus;
import com.revents.eventsourcing.SnapshotRepository;
import com.revents.eventsourcing.SpringR2dbcSnapshotRepository;
import com.revents.eventstore.SpringR2dbcEventStore;
import com.revents.springboot.autoconfig.ReventsDefaultCommandBusAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultEventStoreAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultSerializerAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultSnapshotRepositoryAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultTokenStoreAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsSpringR2dbcAutoconfigure;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class ReventsSpringR2dbcAutoconfigurationIT {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withPropertyValues("revents.scanPackage=com.revents")
        .withConfiguration(AutoConfigurations.of(
            ReventsDefaultCommandBusAutoconfiguration.class,
            ReventsDefaultEventStoreAutoconfiguration.class,
            ReventsDefaultSerializerAutoconfiguration.class,
            ReventsDefaultSnapshotRepositoryAutoconfiguration.class,
            ReventsDefaultTokenStoreAutoconfiguration.class,
            ReventsSpringR2dbcAutoconfigure.class))
        .withUserConfiguration(R2dbcTestDatabaseConfiguration.class);

    @Test
    void shouldCreateSpringR2dbcSnapshotRepository() {
        contextRunner.withPropertyValues("revents.r2dbc.snapshot-repository.enabled=true").run(context ->
            assertThat(context)
                .hasSingleBean(SnapshotRepository.class)
                .getBean(SnapshotRepository.class).isInstanceOf(SpringR2dbcSnapshotRepository.class));
    }

    @Test
    void shouldCreateEventStore() {
        contextRunner.withPropertyValues("revents.r2dbc.event-store.enabled=true").run(context ->
            assertThat(context)
                .hasSingleBean(EventStore.class)
                .getBean(EventStore.class).isInstanceOf(SpringR2dbcEventStore.class));
    }

    @Test
    void shouldCreateCommandBus() {
        contextRunner.withPropertyValues("revents.r2dbc.event-store.enabled=true").run(context ->
            assertThat(context)
                .hasSingleBean(CommandBus.class)
                .getBean(CommandBus.class).isInstanceOf(DefaultCommandBus.class));
    }
}
