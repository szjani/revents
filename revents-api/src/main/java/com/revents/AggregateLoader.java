package com.revents;

import reactor.core.publisher.Mono;

public interface AggregateLoader {

    <T> Mono<T> load(AggregateId<T> aggregateId);
}
