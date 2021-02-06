package com.revents;

import org.immutables.value.Value;

@Value.Immutable
interface TestCommand3 extends BaseAggregateIdAwareCommand {

    @ExpectedRevision
    long revision();
}
