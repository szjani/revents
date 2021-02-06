package com.revents;

import org.immutables.value.Value;

@Value.Immutable
public interface StreamName {

    StreamName ALL = StreamName.of("DEFAULT");

    static StreamName of(String value) {
        return ImmutableStreamName.of(value);
    }

    @Value.Parameter
    String value();
}
