package com.revents;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value.Immutable
@JsonSerialize(as = ImmutableEventMessage.class)
@JsonDeserialize(as = ImmutableEventMessage.class)
public interface EventMessage<P> extends Message<EventMessage.EventId, P, EventMessage.EventMetaData> {

    static <P> ImmutableEventMessage.Builder<P> builder() {
        return ImmutableEventMessage.builder();
    }

    /**
     * Create a clone with filled {@code revision}.
     *
     * @param revision given by the {@link EventStore}
     * @return a new event message
     */
    @Value.Derived
    default EventMessage<P> toPersisted(long revision) {
        return EventMessage.<P>builder()
            .from(this)
            .metaData(EventMetaData.builder()
                .from(metaData())
                .revision(revision)
                .build())
            .build();
    }

    @Value.Style(
        visibility = Value.Style.ImplementationVisibility.PUBLIC,
        defaults = @Value.Immutable(builder = false, copy = false))
    @Value.Immutable
    @JsonSerialize(as = ImmutableEventId.class)
    @JsonDeserialize(as = ImmutableEventId.class)
    interface EventId extends Message.MessageId {

        static EventId of(String id) {
            return ImmutableEventId.of(id);
        }

        static EventId create() {
            return EventId.of(UUID.randomUUID().toString());
        }
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableEventMetaData.class)
    @JsonDeserialize(as = ImmutableEventMetaData.class)
    interface EventMetaData extends Message.MetaData<EventId> {

        static Builder builder() {
            return new EventMetaData.Builder();
        }

        @Override
        @Value.Default
        default EventId id() {
            return EventId.create();
        }

        AggregateId<?> aggregateId();

        class Builder extends ImmutableEventMetaData.Builder implements MetaDataBuilder<Builder> {

            @Override
            public Builder withClock(Clock clock) {
                return created(OffsetDateTime.now(clock));
            }
        }
    }
}
