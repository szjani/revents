package com.revents;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.time.ZonedDateTime;

@Value.Immutable
@JsonSerialize(as = ImmutableTestEvent3.class)
@JsonDeserialize(as = ImmutableTestEvent3.class)
interface TestEvent3 extends BaseTestEvent {

    ZonedDateTime created();
}
