package com.revents;

import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
public interface StreamReadRequest {

    Optional<EventProcessor.EventProcessorId> eventProcessorId();

    @Value.Default
    default StreamName streamName() {
        return StreamName.ALL;
    }

    @Value.Default
    default EventStreamPosition startReadingFrom() {
        return EventStreamPosition.BEGINNING;
    }

    @Value.Default
    default boolean infinite() {
        return false;
    }

    static ImmutableStreamReadRequest.Builder builder() {
        return ImmutableStreamReadRequest.builder();
    }
}
