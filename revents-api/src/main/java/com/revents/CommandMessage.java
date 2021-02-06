package com.revents;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Value.Immutable
@JsonSerialize(as = ImmutableCommandMessage.class)
@JsonDeserialize(as = ImmutableCommandMessage.class)
public interface CommandMessage<P> extends Message<CommandMessage.CommandId, P, CommandMessage.CommandMetaData> {

    static <P> ImmutableCommandMessage.Builder<P> builder() {
        return ImmutableCommandMessage.builder();
    }

    @Value.Style(
        visibility = Value.Style.ImplementationVisibility.PUBLIC,
        defaults = @Value.Immutable(builder = false, copy = false))
    @Value.Immutable
    @JsonSerialize(as = ImmutableCommandId.class)
    @JsonDeserialize(as = ImmutableCommandId.class)
    interface CommandId extends Message.MessageId {

        static CommandId of(String id) {
            return ImmutableCommandId.of(id);
        }
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableCommandMetaData.class)
    @JsonDeserialize(as = ImmutableCommandMetaData.class)
    interface CommandMetaData extends Message.MetaData<CommandId> {

        static Builder builder() {
            return new CommandMetaData.Builder();
        }

        @Override
        @Value.Default
        default CommandId id() {
            return CommandId.of(UUID.randomUUID().toString());
        }

        Optional<AggregateId<?>> aggregateId();

        class Builder extends ImmutableCommandMetaData.Builder implements MetaDataBuilder<Builder> {

            @Override
            public Builder withClock(Clock clock) {
                return created(OffsetDateTime.now(clock));
            }
        }
    }
}
