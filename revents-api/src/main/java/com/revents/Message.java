package com.revents;

import com.fasterxml.jackson.annotation.JsonValue;
import org.immutables.value.Value;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.OptionalLong;

public interface Message<I extends Message.MessageId, P, M extends Message.MetaData<I>> {

    M metaData();

    P payload();

    interface MessageId {

        @JsonValue
        @Value.Parameter
        String rawId();
    }

    interface MetaData<I extends MessageId> {

        I id();

        @Value.Default
        default OffsetDateTime created() {
            return OffsetDateTime.now(ReventsClock.system());
        }

        OptionalLong revision();

        interface MetaDataBuilder<S extends MetaDataBuilder<S>> {

            S withClock(Clock clock);
        }
    }
}
