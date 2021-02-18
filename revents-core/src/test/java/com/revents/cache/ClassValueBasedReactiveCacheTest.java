package com.revents.cache;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ClassValueBasedReactiveCacheTest {

    ClassValueBasedReactiveCache<String> cache;

    @BeforeEach
    void setUp() {
        cache = new ClassValueBasedReactiveCache<>();
    }

    @Test
    void shouldCompleteIfCacheIsEmpty() {
        cache.get(getClass())
            .as(StepVerifier::create)
            .verifyComplete();
    }

    @Test
    void shouldPutAndReturnCachedValue() {
        var expected = "expected";
        cache.put(getClass(), expected)
            .as(StepVerifier::create)
            .verifyComplete();
        cache.get(getClass())
            .as(StepVerifier::create)
            .expectNext(expected)
            .verifyComplete();
    }
}
