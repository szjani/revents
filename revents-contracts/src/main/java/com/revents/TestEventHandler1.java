package com.revents;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public class TestEventHandler1 {

    final Sinks.Many<BaseTestEvent> allEventStream = Sinks.many().multicast().onBackpressureBuffer();
    final Sinks.Many<BaseTestEvent> eventStream1 = Sinks.many().multicast().onBackpressureBuffer();

    @EventHandler
    public Mono<Void> eventHandler1(TestEvent1 event, EventContext context) {
        return Mono.fromRunnable(() -> eventStream1.tryEmitNext(event));
    }

    @EventHandler
    public Mono<Void> allBaseTestEventHandler(BaseTestEvent event, EventContext context) {
        return Mono.fromRunnable(() -> allEventStream.tryEmitNext(event));
    }
}
