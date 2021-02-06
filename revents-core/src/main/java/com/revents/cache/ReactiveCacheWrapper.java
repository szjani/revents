package com.revents.cache;

import static java.util.Objects.requireNonNull;

import com.hotels.molten.cache.ReactiveCache;
import reactor.core.publisher.Mono;

public class ReactiveCacheWrapper<K, V> implements ReactiveExtendedCache<K, V> {

    private final ReactiveCache<K, V> wrapped;

    public ReactiveCacheWrapper(ReactiveCache<K, V> wrapped) {
        this.wrapped = requireNonNull(wrapped);
    }

    @Override
    public Mono<V> get(K key) {
        return wrapped.get(key);
    }

    @Override
    public Mono<Void> put(K key, V value) {
        return wrapped.put(key, value);
    }
}
