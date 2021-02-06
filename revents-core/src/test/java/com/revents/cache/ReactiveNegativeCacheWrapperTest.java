package com.revents.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotels.molten.cache.ReactiveMapCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ExtendWith(MockitoExtension.class)
class ReactiveNegativeCacheWrapperTest {

    static final String A_KEY_1 = "key1";
    static final String A_VALUE_1 = "value1";
    static final ReactiveNegativeCacheWrapper.Wrapped<String> A_WRAPPED_VALUE_1 =
        new ReactiveNegativeCacheWrapper.Wrapped<>(A_VALUE_1);
    static final ReactiveNegativeCacheWrapper.Wrapped<String> AN_EMPTY_WRAPPED_VALUE =
        ReactiveNegativeCacheWrapper.Wrapped.empty();

    Map<String, ReactiveNegativeCacheWrapper.Wrapped<String>> storage;
    ReactiveExtendedCache<String, ReactiveNegativeCacheWrapper.Wrapped<String>> negativeCache;
    ReactiveExtendedCache<String, String> unWrappedCacheView;

    @BeforeEach
    void setUp() {
        storage = new ConcurrentHashMap<>();
        ReactiveNegativeCacheWrapper<String, String> negativeCacheWrapper =
            new ReactiveNegativeCacheWrapper<>(new ReactiveMapCache<>(storage));
        negativeCache = negativeCacheWrapper;
        unWrappedCacheView = negativeCacheWrapper.unWrappedView();
    }

    @Test
    void shouldStoreValueDirectly() {
        negativeCache.put(A_KEY_1, A_WRAPPED_VALUE_1)
            .as(StepVerifier::create)
            .verifyComplete();

        checkExpectedValue(A_KEY_1, A_WRAPPED_VALUE_1);
    }

    @Test
    void shouldStoreNullDirectly() {
        negativeCache.put(A_KEY_1, AN_EMPTY_WRAPPED_VALUE)
            .as(StepVerifier::create)
            .verifyComplete();

        checkExpectedValue(A_KEY_1, AN_EMPTY_WRAPPED_VALUE);
    }

    @Test
    void shouldStoreValueViaView() {
        unWrappedCacheView.put(A_KEY_1, A_VALUE_1)
            .as(StepVerifier::create)
            .verifyComplete();

        checkExpectedValue(A_KEY_1, A_WRAPPED_VALUE_1);
    }

    @Test
    void shouldStoreNullViaView() {
        unWrappedCacheView.put(A_KEY_1, null)
            .as(StepVerifier::create)
            .verifyComplete();

        checkExpectedValue(A_KEY_1, AN_EMPTY_WRAPPED_VALUE);
    }

    @Test
    void shouldStoreValueDirectlyViaOperator() {
        Mono.just(A_WRAPPED_VALUE_1)
            .as(negativeCache.withKey(A_KEY_1))
            .as(StepVerifier::create)
            .expectNext(A_WRAPPED_VALUE_1)
            .verifyComplete();

        checkExpectedValue(A_KEY_1, A_WRAPPED_VALUE_1);
    }

    @Test
    void shouldStoreNullDirectlyViaOperator() {
        Mono.just(AN_EMPTY_WRAPPED_VALUE)
            .as(negativeCache.withKey(A_KEY_1))
            .as(StepVerifier::create)
            .expectNext(AN_EMPTY_WRAPPED_VALUE)
            .verifyComplete();

        checkExpectedValue(A_KEY_1, AN_EMPTY_WRAPPED_VALUE);
    }

    @Test
    void shouldStoreValueViaViewOperator() {
        Mono.just(A_VALUE_1)
            .as(unWrappedCacheView.withKey(A_KEY_1))
            .as(StepVerifier::create)
            .expectNext(A_VALUE_1)
            .verifyComplete();

        checkExpectedValue(A_KEY_1, A_WRAPPED_VALUE_1);
    }

    @Test
    void shouldStoreNullViaViewOperator() {
        Mono.<String>empty()
            .as(unWrappedCacheView.withKey(A_KEY_1))
            .as(StepVerifier::create)
            .verifyComplete();

        checkExpectedValue(A_KEY_1, AN_EMPTY_WRAPPED_VALUE);
    }

    void checkExpectedValue(String key, ReactiveNegativeCacheWrapper.Wrapped<String> wrappedValue) {
        negativeCache.get(key)
            .as(StepVerifier::create)
            .expectNext(wrappedValue)
            .verifyComplete();

        wrappedValue.getValue().ifPresentOrElse(expectedValue -> unWrappedCacheView.get(key)
                .as(StepVerifier::create)
                .expectNext(expectedValue)
                .verifyComplete(),
            () -> unWrappedCacheView.get(key)
                .as(StepVerifier::create)
                .verifyComplete());

        assertThat(storage.get(key)).isEqualTo(wrappedValue);
    }
}