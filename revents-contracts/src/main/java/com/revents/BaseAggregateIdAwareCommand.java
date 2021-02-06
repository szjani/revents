package com.revents;

interface BaseAggregateIdAwareCommand extends BaseTestCommand {

    @TargetAggregate
    String id();
}
