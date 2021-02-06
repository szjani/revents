package com.revents.springboot.autoconfig;

import static java.util.stream.Collectors.toList;

import com.revents.EventMessage;
import com.revents.EventProcessor;
import com.revents.EventProcessor.EventProcessorId;
import com.revents.EventStore;
import com.revents.MessageInterceptor;
import com.revents.eventstore.TrackingEventProcessor;
import com.revents.spring.EventHandlerBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(ReventsSpringProperties.class)
public class ReventsDefaultEventProcessorAutoconfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ReventsDefaultEventProcessorAutoconfiguration.class);

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Bean("eventMessageDispatchInterceptors")
    @ConditionalOnMissingBean(name = "eventMessageDispatchInterceptors")
    Map<EventProcessorId, List<MessageInterceptor<EventMessage<?>, Void>>> eventMessageDispatchInterceptors() {
        return Map.of();
    }

    @Bean("eventMessageHandlerInterceptors")
    @ConditionalOnMissingBean(name = "eventMessageHandlerInterceptors")
    Map<EventProcessorId, List<MessageInterceptor<EventMessage<?>, Void>>> eventMessageHandlerInterceptors() {
        return Map.of();
    }

    @SuppressWarnings("checkstyle:linelength")
    @Bean("eventProcessors")
    @ConditionalOnMissingBean(name = "eventProcessors")
    List<EventProcessor> eventProcessors(
        EventStore eventStore,
        @EventHandlerBean List<Object> eventHandlerBeans,
        @Qualifier("eventMessageDispatchInterceptors") Map<EventProcessorId, List<MessageInterceptor<EventMessage<?>, Void>>> dispatchInterceptors,
        @Qualifier("eventMessageHandlerInterceptors") Map<EventProcessorId, List<MessageInterceptor<EventMessage<?>, Void>>> handlerInterceptors) {

        LOG.info("Found {} @EventHandlerBeans {}", eventHandlerBeans.size(), eventHandlerBeans);
        Map<EventProcessorId, List<Object>> processorIdMap = eventHandlerBeans.stream()
            .collect(Collectors.groupingBy(eventHandlerBean ->
                EventProcessorId.of(eventHandlerBean.getClass().getAnnotation(EventHandlerBean.class).processorId())));

        return processorIdMap.entrySet().stream()
            .map(entry -> TrackingEventProcessor.create(config -> config
                .processorId(entry.getKey())
                .eventStore(eventStore)
                .eventMessageDispatchInterceptors(dispatchInterceptors.getOrDefault(entry.getKey(), List.of()))
                .annotationBasedEventMessageHandler(messageHandlerConfig -> messageHandlerConfig
                    .eventMessageHandlerInterceptors(handlerInterceptors.getOrDefault(entry.getKey(), List.of()))
                    .eventHandlers(entry.getValue()))))
            .peek(eventProcessor -> beanFactory.registerSingleton(
                "event-processor-" + eventProcessor.processorId().name(), eventProcessor))
            .collect(toList());
    }

    @Bean
    @ConditionalOnProperty(
        name = "revents.event-processor-manager.enabled",
        havingValue = "true",
        matchIfMissing = true)
    ReventsSpringEventProcessorManager eventProcessorManager(
        @Qualifier("eventProcessors") List<EventProcessor> eventProcessors) {

        return new ReventsSpringEventProcessorManager(eventProcessors);
    }

    public static class ReventsSpringEventProcessorManager {

        private final List<EventProcessor> eventProcessors;

        ReventsSpringEventProcessorManager(List<EventProcessor> eventProcessors) {
            this.eventProcessors = eventProcessors;
        }

        /**
         * Start all the event processors.
         *
         * @param event context refreshed event
         */
        @EventListener
        public void startProcessors(ContextRefreshedEvent event) {
            LOG.info("Starting all event processors...");
            eventProcessors.forEach(EventProcessor::run);
            LOG.info("All event processors have been started");
        }

        /**
         * Stop all the event processors.
         *
         * @param event context stopped event
         */
        @EventListener
        public void stopProcessors(ContextStoppedEvent event) {
            LOG.info("Stopping all event processors...");
            eventProcessors.forEach(EventProcessor::stop);
            LOG.info("All event processors have been stopped");
        }
    }
}
