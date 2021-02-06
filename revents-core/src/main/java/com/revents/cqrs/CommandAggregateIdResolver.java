package com.revents.cqrs;

import com.revents.AggregateId;
import reactor.core.publisher.Mono;

public interface CommandAggregateIdResolver {

    <C> Mono<AggregateId<?>> aggregateIdOf(C command);
}
