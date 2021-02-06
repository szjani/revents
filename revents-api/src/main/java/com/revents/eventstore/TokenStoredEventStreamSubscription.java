package com.revents.eventstore;

import com.revents.EventMessage;
import com.revents.EventProcessor;
import com.revents.EventStreamPosition;
import com.revents.EventStreamSubscription;
import org.immutables.value.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Value.Immutable
public interface TokenStoredEventStreamSubscription extends EventStreamSubscription {

    static ImmutableTokenStoredEventStreamSubscription.Builder builder() {
        return ImmutableTokenStoredEventStreamSubscription.builder();
    }

    TokenStore tokenStore();

    Optional<EventProcessor.EventProcessorId> eventProcessorId();

    @Override
    default Mono<Void> acknowledge(List<EventMessage<?>> eventMessages) {
        return Mono.justOrEmpty(eventProcessorId())
            .flatMapMany(eventProcessorId -> Flux.fromIterable(eventMessages)
                .flatMap(msg -> tokenStore().save(eventProcessorId, EventStreamPosition.forEventMessage(msg))))
            .then();
    }
}
