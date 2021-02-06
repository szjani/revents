package com.revents;

import static org.assertj.core.api.Assertions.assertThat;

import com.revents.cqrs.DefaultCommandBus;
import com.revents.cqrs.LocalCommandGateway;
import com.revents.eventsourcing.InMemorySnapshotRepository;
import com.revents.eventsourcing.SnapshotRepository;
import com.revents.eventstore.InMemoryEventStore;
import com.revents.eventstore.InMemoryTokenStore;
import com.revents.eventstore.TokenStore;
import com.revents.eventstore.TrackingEventProcessor;
import com.revents.serialization.Serializer;
import com.revents.spring.EventHandlerBean;
import com.revents.springboot.autoconfig.ReventsDefaultCommandBusAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultEventProcessorAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultEventStoreAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultSerializerAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultSnapshotRepositoryAutoconfiguration;
import com.revents.springboot.autoconfig.ReventsDefaultTokenStoreAutoconfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public class ReventsSpringDefaultAutoconfigurationIT {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withPropertyValues("revents.scanPackage=com.revents")
        .withConfiguration(AutoConfigurations.of(
            ReventsDefaultEventProcessorAutoconfiguration.class,
            ReventsDefaultCommandBusAutoconfiguration.class,
            ReventsDefaultEventStoreAutoconfiguration.class,
            ReventsDefaultSerializerAutoconfiguration.class,
            ReventsDefaultSnapshotRepositoryAutoconfiguration.class,
            ReventsDefaultTokenStoreAutoconfiguration.class));

    @Test
    @SuppressWarnings("PMD.JUnitAssertionsShouldIncludeMessage")
    void shouldDefaultConfiguration() {
        contextRunner.run(context -> {
            assertThat(context)
                .hasBean("eventSerializer")
                .hasBean("snapshotSerializer")
                .hasBean("tokenStoreForConsumerId");
            assertThat(context).getBean("eventSerializer", Serializer.class)
                .isInstanceOf(Serializer.JacksonSerializer.class);
            assertThat(context).getBean("snapshotSerializer", Serializer.class)
                .isInstanceOf(Serializer.JacksonSerializer.class);
            assertThat(context).getBean(CommandBus.class).isInstanceOf(DefaultCommandBus.class);
            assertThat(context).getBean(CommandGateway.class).isInstanceOf(LocalCommandGateway.class);
            assertThat(context).getBean(EventStore.class).isInstanceOf(InMemoryEventStore.class);
            assertThat(context).getBean(SnapshotRepository.class).isInstanceOf(InMemorySnapshotRepository.class);
            assertThat(context).getBean(TokenStore.class).isInstanceOf(InMemoryTokenStore.class);
        });
    }

    @Test
    void shouldFindEventHandlerBean() {
        contextRunner
            .withBean("eventHandler1", TestEventHandler1.class, TestEventHandler1::new)
            .run(context -> assertThat(context)
                .hasBean("event-processor-default")
                .getBean("event-processor-default")
                    .isInstanceOf(TrackingEventProcessor.class));
    }

    @Test
    void shouldRegisterProcessorWithExplicitId() {
        contextRunner
            .withBean("eventHandler1", TestEventHandler1.class, TestEventHandler1::new)
            .withBean("eventHandler2", TestEventHandler2.class, TestEventHandler2::new)
            .withBean("eventHandler3", TestEventHandler3.class, TestEventHandler3::new)
            .run(context -> {
                assertThat(context)
                    .hasBean("event-processor-foo")
                    .getBean("event-processor-foo")
                    .isInstanceOf(TrackingEventProcessor.class);
                assertThat(context).getBeans(TrackingEventProcessor.class).hasSize(2);
            });
    }

    @EventHandlerBean
    static class TestEventHandler1 {
    }

    @EventHandlerBean(processorId = "foo")
    static class TestEventHandler2 {
    }

    @EventHandlerBean(processorId = "foo")
    static class TestEventHandler3 {
    }
}
