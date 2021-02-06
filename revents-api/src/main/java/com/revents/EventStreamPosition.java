package com.revents;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableEventStreamPosition.class)
@JsonDeserialize(as = ImmutableEventStreamPosition.class)
public interface EventStreamPosition {

    /**
     * Points to the beginning of an event stream.
     */
    EventStreamPosition BEGINNING = ImmutableEventStreamPosition.builder().build();

    static EventStreamPosition of(EventMessage.EventId eventId) {
        return ImmutableEventStreamPosition.of(Optional.of(eventId));
    }

    @Value.Parameter
    Optional<EventMessage.EventId> lastProcessedEvent();

    /**
     * Create a position from an {@code EventMessage}.
     *
     * @param eventMessage represents the position
     * @param <P> type of the payload
     * @return the position
     */
    static <P> EventStreamPosition forEventMessage(EventMessage<P> eventMessage) {
        return ImmutableEventStreamPosition.builder()
            .lastProcessedEvent(eventMessage.metaData().id())
            .build();
    }
}
