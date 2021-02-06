package com.revents.eventstore;

import com.revents.EventProcessor;
import com.revents.EventStreamPosition;
import reactor.core.publisher.Mono;

public interface TokenStore {

    Mono<EventStreamPosition> tokenFor(EventProcessor.EventProcessorId processorId);

    Mono<Void> save(EventProcessor.EventProcessorId processorId, EventStreamPosition token);
}
