package com.revents;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableTestEvent1.class)
@JsonDeserialize(as = ImmutableTestEvent1.class)
public interface TestEvent1 extends BaseTestEvent {

    String PREFIX = "TestAggregate1-";

    @DynamicStream(prefix = PREFIX)
    String aggregateId();
}
