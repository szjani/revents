package com.revents;

import org.immutables.value.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

public interface EventStreamSubscription {

    Flux<EventMessage<?>> events();

    Mono<Void> acknowledge(List<EventMessage<?>> eventMessages);

    default Mono<Void> acknowledge(EventMessage<?>... eventMessages) {
        return acknowledge(List.of(eventMessages));
    }

    @Value.Immutable
    interface AckCallbackEventStreamSubscription extends EventStreamSubscription {

        Function<List<EventMessage<?>>, Mono<Void>> acknowledgeCallback();

        @Override
        default Mono<Void> acknowledge(List<EventMessage<?>> eventMessages) {
            return acknowledgeCallback().apply(eventMessages);
        }
    }
}
