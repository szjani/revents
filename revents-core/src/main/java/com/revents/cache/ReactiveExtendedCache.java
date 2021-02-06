package com.revents.cache;

import com.hotels.molten.cache.ReactiveCache;
import com.hotels.molten.cache.ReactiveMapCache;
import com.revents.cache.ReactiveNegativeCacheWrapper.Wrapped;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public interface ReactiveExtendedCache<K, V> extends ReactiveCache<K, V> {

    static <K, V> ReactiveExtendedCache<K, V> cacheOver(ReactiveCache<K, V> cache) {
        return new ReactiveCacheWrapper<>(cache);
    }

    static <K, V> ReactiveExtendedCache<K, V> negativeCacheOver(ReactiveCache<K, Wrapped<V>> cache) {
        return new ReactiveNegativeCacheWrapper<>(cache).unWrappedView();
    }

    static <K, V> ReactiveExtendedCache<K, V> inMemoryCache() {
        return cacheOver(new ReactiveMapCache<>(new ConcurrentHashMap<>()));
    }

    static <K, V> ReactiveExtendedCache<K, V> inMemoryNegativeCache() {
        return negativeCacheOver(new ReactiveMapCache<>(new ConcurrentHashMap<>()));
    }

    /**
     * Return the value stored to {@code key} if any,
     * otherwise subscribe to the given {@code Mono}
     * and store its return value in the cache.
     *
     * <pre>
     * return Mono.fromCallable(() -&gt; expensiveServiceCall(key))
     *   .as(serviceCallCache.withKey(key));
     * </pre>
     *
     * @param key the cache key
     * @return a function
     */
    default Function<Mono<V>, Mono<V>> withKey(K key) {
        return withKey(key, Function.identity(), Function.identity());
    }

    /**
     * Return {@code valueExtractor.apply(value)}, if {@code value} already stored to {@code key},
     * otherwise subscribe to the given {@code Mono}
     * and store its return value as {@code valueWrapper.apply(returnValue)} in the cache.
     *
     * @param key the cache key
     * @param valueWrapper transforming the non-cached value to be storable
     * @param valueExtractor transforming the cached value
     * @param <U> type of the exposed value
     * @return a function
     */
    default <U> Function<Mono<U>, Mono<U>> withKey(K key,
                                                   Function<Mono<U>, Mono<V>> valueWrapper,
                                                   Function<Mono<V>, Mono<U>> valueExtractor) {
        return nonCachedMono -> get(key)
            .switchIfEmpty(nonCachedMono
                .as(valueWrapper)
                .flatMap(nonCachedWrapped -> put(key, nonCachedWrapped)
                    .thenReturn(nonCachedWrapped)))
            .as(valueExtractor);
    }
}
