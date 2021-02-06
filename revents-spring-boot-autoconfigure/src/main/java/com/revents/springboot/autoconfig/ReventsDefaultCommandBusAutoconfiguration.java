package com.revents.springboot.autoconfig;

import com.revents.AggregateLoader;
import com.revents.CommandBus;
import com.revents.CommandGateway;
import com.revents.CommandMessage;
import com.revents.CommandResult;
import com.revents.EventStore;
import com.revents.MessageHandler;
import com.revents.MessageInterceptor;
import com.revents.cqrs.AnnotationAwareCommandMessageDispatcher;
import com.revents.cqrs.CommandAggregateIdResolver;
import com.revents.cqrs.CommandRevisionResolver;
import com.revents.cqrs.DefaultCommandBus;
import com.revents.cqrs.EventStoringCommandInterceptor;
import com.revents.cqrs.LocalCommandGateway;
import com.revents.eventsourcing.EventSourcedBasedAggregateLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties(ReventsSpringProperties.class)
public class ReventsDefaultCommandBusAutoconfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ReventsDefaultCommandBusAutoconfiguration.class);

    @Bean("commandMessageInterceptors")
    @ConditionalOnMissingBean(name = "commandMessageInterceptors")
    List<MessageInterceptor<CommandMessage<?>, CommandResult>> commandMessageInterceptors(EventStore eventStore) {
        return List.of(new EventStoringCommandInterceptor(eventStore));
    }

    @Bean
    @ConditionalOnMissingBean
    AggregateLoader aggregateLoader(EventStore eventStore) {
        return EventSourcedBasedAggregateLoader.create(config -> config.eventStore(eventStore));
    }

    @SuppressWarnings("checkstyle:linelength")
    @Bean
    @ConditionalOnMissingBean
    CommandBus annotationBasedCommandBus(
        @Qualifier("commandMessageHandler") MessageHandler<CommandMessage<?>, CommandResult> commandMessageHandler,
        @Qualifier("commandMessageInterceptors") List<MessageInterceptor<CommandMessage<?>, CommandResult>> interceptors) {

        return DefaultCommandBus.create(busConfig -> busConfig
            .commandMessageInterceptors(interceptors)
            .commandMessageHandler(commandMessageHandler));
    }

    @Bean
    @ConditionalOnMissingBean
    CommandAggregateIdResolver commandAggregateIdResolver(
        AggregateLoader aggregateLoader,
        @Autowired(required = false) @Qualifier("aggregateCommandHandlers") List<Class<?>> aggregates,
        ReventsSpringProperties properties) {

        return annotationAwareCommandMessageDispatcher(aggregateLoader, aggregates, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    CommandRevisionResolver commandRevisionResolver() {
        return CommandRevisionResolver.ANNOTATION_BASED;
    }

    @Bean("commandMessageHandler")
    @ConditionalOnMissingBean(name = "commandMessageHandler")
    MessageHandler<CommandMessage<?>, CommandResult> commandMessageHandler(
        AggregateLoader aggregateLoader,
        @Autowired(required = false) @Qualifier("aggregateCommandHandlers") List<Class<?>> aggregates,
        ReventsSpringProperties properties) {

        return annotationAwareCommandMessageDispatcher(aggregateLoader, aggregates, properties);
    }

    @Bean
    AnnotationAwareCommandMessageDispatcher annotationAwareCommandMessageDispatcher(
        AggregateLoader aggregateLoader,
        @Autowired(required = false) @Qualifier("aggregateCommandHandlers") List<Class<?>> aggregateClasses,
        ReventsSpringProperties properties) {

        return AnnotationAwareCommandMessageDispatcher.create(config -> {
            config.aggregateLoader(aggregateLoader);
            if (aggregateClasses != null) {
                LOG.info("Defined aggregate classes: {}", aggregateClasses);
                config.aggregateCommandHandlers(aggregateClasses);
            } else {
                LOG.info("No aggregate classes are defined, scanning package: {}", properties.getScanPackage());
                config.scanAggregateClassesIn(properties.getScanPackage());
            }
            return config;
        });
    }

    @Bean
    @ConditionalOnMissingBean
    CommandGateway commandGateway(CommandBus commandBus,
                                  CommandAggregateIdResolver commandAggregateIdResolver,
                                  CommandRevisionResolver commandRevisionResolver) {
        return LocalCommandGateway.create(config -> config
            .commandBus(commandBus)
            .commandAggregateIdResolver(commandAggregateIdResolver)
            .commandRevisionResolver(commandRevisionResolver));
    }
}
