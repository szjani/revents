package com.revents.cache;

import com.hotels.molten.cache.ReactiveCache;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link ClassValue}-based reactive cache implementation.
 *
 * @param <V> type of the cached value
 */
public class ClassValueBasedReactiveCache<V> implements ReactiveCache<Class<?>, V> {

    private final ClassValue<AtomicReference<V>> classValue = new ClassValue<>() {

        @Override
        protected AtomicReference<V> computeValue(Class<?> type) {
            return new AtomicReference<>();
        }
    };

    @Override
    public Mono<V> get(Class<?> key) {
        return Mono.fromCallable(() -> classValue.get(key).get());
    }

    @Override
    public Mono<Void> put(Class<?> key, V value) {
        return Mono.fromRunnable(() -> classValue.get(key).set(value));
    }
}
