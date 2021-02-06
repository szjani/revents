package com.revents;

import org.immutables.value.Value;

public interface EventProcessor extends Runnable {

    EventProcessorId processorId();

    void stop();

    @Value.Immutable
    interface EventProcessorId {

        String DEFAULT_NAME = "default";
        EventProcessorId DEFAULT = EventProcessorId.of(DEFAULT_NAME);

        static EventProcessorId of(String processorId) {
            return ImmutableEventProcessorId.of(processorId);
        }

        @Value.Parameter
        String name();
    }
}
