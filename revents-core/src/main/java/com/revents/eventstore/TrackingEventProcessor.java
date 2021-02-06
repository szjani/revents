package com.revents.eventstore;

import com.google.common.collect.ImmutableList;
import com.revents.EventMessage;
import com.revents.EventProcessor;
import com.revents.EventStore;
import com.revents.EventStreamSubscription;
import com.revents.MessageHandler;
import com.revents.MessageInterceptor;
import com.revents.MessageInterceptorChain;
import com.revents.StreamName;
import com.revents.StreamReadRequest;
import com.revents.cqrs.AnnotationAwareEventMessageDispatcher;
import com.revents.cqrs.AnnotationAwareEventMessageDispatcher.AnnotationAwareEventMessageDispatcherConfig;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.UnaryOperator;

public class TrackingEventProcessor implements EventProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(TrackingEventProcessor.class);

    private final EventProcessorId id;
    private final MessageHandler<EventMessage<?>, Void> eventMessageHandler;
    private final Mono<Void> processor;
    private Disposable disposable;

    private TrackingEventProcessor(TrackingEventProcessorConfig processorConfig) {
        id = processorConfig.processorId();
        eventMessageHandler = processorConfig.eventMessageHandler();
        EventStore eventStore = processorConfig.eventStore();
        EventStreamSubscription eventStreamSubscription = eventStore.readEventStream(StreamReadRequest.builder()
            .eventProcessorId(processorId())
            .streamName(processorConfig.eventStream())
            .infinite(true)
            .build());
        processor = eventStreamSubscription.events()
            .concatMap(eventMessage -> MessageInterceptorChain.create(
                processorConfig.eventMessageDispatchInterceptors(), eventMessageHandler)
                .handle(eventMessage)
                .contextWrite(context -> context
                    .put("eid", eventMessage.metaData().id().rawId())
                    .put(EventStreamSubscription.class, eventStreamSubscription)))
            .then();
    }

    public static TrackingEventProcessor create(UnaryOperator<TrackingEventProcessorConfig.Builder> init) {
        return new TrackingEventProcessor(init.apply(new TrackingEventProcessorConfig.Builder()).build());
    }

    @Override
    public void run() {
        LOG.info("Starting tracking event processor={}", processorId());
        disposable = processor
            .subscribeOn(Schedulers.parallel())
            .subscribe();
        LOG.info("Tracking event processor has been started={}", processorId());
    }

    @Override
    public void stop() {
        LOG.info("Stopping tracking event processor={}", processorId());
        disposable.dispose();
        LOG.info("Tracking event processor has been stopped={}", processorId());
    }

    @Override
    public EventProcessorId processorId() {
        return id;
    }

    @Value.Immutable
    public interface TrackingEventProcessorConfig {

        EventStore eventStore();

        List<MessageInterceptor<EventMessage<?>, Void>> eventMessageDispatchInterceptors();

        MessageHandler<EventMessage<?>, Void> eventMessageHandler();

        @Value.Default
        default EventProcessorId processorId() {
            return EventProcessorId.DEFAULT;
        }

        @Value.Default
        default StreamName eventStream() {
            return StreamName.ALL;
        }

        /**
         * Checks if the {@code eventMessageDispatchInterceptors} contains
         * an {@link EventMessageAcknowledgeInterceptor} instance. If so then it
         * leaves the configuration as is, otherwise add a new {@link EventMessageAcknowledgeInterceptor}
         * instance to the beginning of the interceptor list.
         *
         * @return the config that contains a {@code EventMessageAcknowledgeInterceptor}
         */
        @Value.Check
        default TrackingEventProcessorConfig eventMessageAcknowledgeInterceptorCheck() {
            TrackingEventProcessorConfig result = this;
            if (eventMessageDispatchInterceptors().stream()
                .noneMatch(EventMessageAcknowledgeInterceptor.class::isInstance)) {
                result = new Builder()
                    .from(this)
                    .eventMessageDispatchInterceptors(ImmutableList.<MessageInterceptor<EventMessage<?>, Void>>builder()
                        .add(new EventMessageAcknowledgeInterceptor())
                        .addAll(eventMessageDispatchInterceptors())
                        .build())
                    .addEventMessageDispatchInterceptors()
                    .build();
            }
            return result;
        }

        class Builder extends ImmutableTrackingEventProcessorConfig.Builder {

            public Builder annotationBasedEventMessageHandler(
                UnaryOperator<AnnotationAwareEventMessageDispatcherConfig.Builder> init) {
                return eventMessageHandler(AnnotationAwareEventMessageDispatcher.create(init));
            }
        }
    }
}
