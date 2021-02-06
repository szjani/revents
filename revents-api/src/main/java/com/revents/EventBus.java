package com.revents;

import reactor.core.publisher.Mono;

public interface EventBus {

    <P> Mono<Void> publish(EventMessage<P> eventMessage);
}
