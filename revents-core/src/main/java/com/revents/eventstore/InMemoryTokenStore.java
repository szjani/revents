package com.revents.eventstore;

import static com.revents.log.TransformContextToMdc.monoLogOnSuccess;

import com.revents.EventProcessor.EventProcessorId;
import com.revents.EventStreamPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTokenStore implements TokenStore {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryTokenStore.class);

    private final Map<String, EventStreamPosition> tokens = new ConcurrentHashMap<>();

    @Override
    public Mono<EventStreamPosition> tokenFor(EventProcessorId consumerId) {
        return Mono.fromCallable(() -> tokens.get(consumerId.name()))
            .defaultIfEmpty(EventStreamPosition.BEGINNING);
    }

    @Override
    public Mono<Void> save(EventProcessorId consumerId, EventStreamPosition token) {
        return Mono.<Void>fromRunnable(() -> tokens.put(consumerId.name(), token))
            .transform(monoLogOnSuccess(x -> LOG.debug("Token saved for consumerId={}, {}", consumerId, token)));
    }

    /**
     * Reset the in-memory store. Use only for testing purposes.
     */
    public void reset() {
        tokens.clear();
    }
}
