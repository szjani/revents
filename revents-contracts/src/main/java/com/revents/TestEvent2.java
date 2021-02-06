package com.revents;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableTestEvent2.class)
@JsonDeserialize(as = ImmutableTestEvent2.class)
@FixStream("TestEvent2")
interface TestEvent2 extends BaseTestEvent {
}
