package com.revents;

import com.revents.EventMessage.EventMetaData;
import org.immutables.value.Value;
import reactor.core.publisher.Mono;

import java.time.Clock;

@Value.Immutable
public interface EventContext {

    static ImmutableEventContext.Builder builder() {
        return ImmutableEventContext.builder();
    }

    /**
     * Create a context for the given {@code eventMessage}
     * to pass that to the event handler.
     *
     * @param eventMessage the message being processed
     * @param <P> type of the payload
     * @return a new context
     */
    static <P> Mono<EventContext> createFor(EventMessage<P> eventMessage) {
        return Mono.deferContextual(context -> Mono.just(context.get(Clock.class)))
            .map(clock -> EventContext.builder()
                .clock(clock)
                .metaData(eventMessage.metaData())
                .build())
            .as(ReventsClock.monoContextualClock())
            .cast(EventContext.class);
    }

    EventMetaData metaData();

    Clock clock();
}
