package com.revents.eventstore;

import com.revents.EventMessage;
import com.revents.EventStreamSubscription;
import com.revents.MessageInterceptor;
import com.revents.MessageInterceptorChain;
import reactor.core.publisher.Mono;

public final class EventMessageAcknowledgeInterceptor implements MessageInterceptor<EventMessage<?>, Void> {

    @Override
    public Mono<Void> handle(EventMessage<?> message, MessageInterceptorChain<EventMessage<?>, Void> chain) {
        return Mono.deferContextual(contextView -> chain.handle(message)
            .then(contextView.get(EventStreamSubscription.class).acknowledge(message)));
    }
}
