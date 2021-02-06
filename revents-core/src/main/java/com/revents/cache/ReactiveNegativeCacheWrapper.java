package com.revents.cache;

import com.google.common.base.MoreObjects;
import com.hotels.molten.cache.ReactiveCache;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

class ReactiveNegativeCacheWrapper<K, V>
    extends ReactiveCacheWrapper<K, ReactiveNegativeCacheWrapper.Wrapped<V>> {

    public ReactiveNegativeCacheWrapper(ReactiveCache<K, Wrapped<V>> wrapped) {
        super(wrapped);
    }

    ReactiveExtendedCache<K, V> unWrappedView() {
        return new ReactiveExtendedCache<>() {

            @Override
            public Mono<V> get(K key) {
                return ReactiveNegativeCacheWrapper.super.get(key)
                    .flatMap(Wrapped::valueMono);
            }

            @Override
            public Mono<Void> put(K key, V value) {
                return ReactiveNegativeCacheWrapper.super.put(key,
                    value == null ? Wrapped.empty() : new Wrapped<>(value));
            }

            @Override
            public Function<Mono<V>, Mono<V>> withKey(K key) {
                return ReactiveNegativeCacheWrapper.super.withKey(key,
                    valueMono -> valueMono.map(Wrapped::new).switchIfEmpty(Mono.just(Wrapped.empty())),
                    wrappedMono -> wrappedMono.flatMap(Wrapped::valueMono));
            }
        };
    }

    public static final class Wrapped<V> {

        private static final Wrapped<?> EMPTY_WRAPPED = new Wrapped<>(null);

        private final V value;

        public Wrapped(@Nullable V value) {
            this.value = value;
        }

        @SuppressWarnings("unchecked")
        public static <T> Wrapped<T> empty() {
            return (Wrapped<T>) EMPTY_WRAPPED;
        }

        public Optional<V> getValue() {
            return Optional.ofNullable(value);
        }

        public Mono<V> valueMono() {
            return Mono.justOrEmpty(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Wrapped<?> wrapped = (Wrapped<?>) o;
            return Objects.equals(value, wrapped.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .toString();
        }
    }
}
