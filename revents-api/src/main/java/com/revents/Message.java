package com.revents;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import org.immutables.value.Value;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.OptionalLong;

public interface Message<I extends Message.MessageId, P, M extends Message.MetaData<I>> {

    M metaData();

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
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
            return OffsetDateTime.now(ReventsClock.system()).truncatedTo(ChronoUnit.MILLIS);
        }

        OptionalLong revision();

        interface MetaDataBuilder<S extends MetaDataBuilder<S>> {

            S withClock(Clock clock);
        }
    }
}
