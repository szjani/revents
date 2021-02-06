package com.revents.log;

import com.revents.ReventsException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SuppressWarnings("PMD.TooManyMethods")
public final class TransformContextToMdc {

    private TransformContextToMdc() {
    }

    public static <T> Function<ContextView, Mono<? extends T>> decorateCallable(Callable<? extends T> callable) {
        return context -> Mono.fromCallable(innerDecorateCallable(context, callable));
    }

    public static <T> Function<ContextView, Publisher<T>> decorateStreamSupplier(
        Supplier<Stream<? extends T>> supplier) {
        return context -> Flux.fromStream(innerDecorateSupplier(context, supplier));
    }

    public static <T> MonoMdcDecorator<T> monoLogOnNext(Consumer<T> consumer) {
        return new MonoMdcDecorator<>((context, mono) -> mono
            .doOnNext(item -> innerRunWithMdc(context, () -> consumer.accept(item))));
    }

    public static <T> Function<Mono<T>, Mono<T>> monoLogOnSubscribe(Consumer<? super Subscription> consumer) {
        return new MonoMdcDecorator<>((context, mono) -> mono
            .doOnSubscribe(subscription -> innerRunWithMdc(context, () -> consumer.accept(subscription))));
    }

    public static <T> Function<Mono<T>, Mono<T>> monoLogOnError(Consumer<? super Throwable> consumer) {
        return new MonoMdcDecorator<>((context, mono) -> mono
            .doOnError(error -> innerRunWithMdc(context, () -> consumer.accept(error))));
    }

    public static <T> Function<Mono<T>, Mono<T>> monoLogOnSuccess(Consumer<? super T> consumer) {
        return new MonoMdcDecorator<>((context, mono) -> mono
            .doOnSuccess(item -> innerRunWithMdc(context, () -> consumer.accept(item))));
    }

    public static <T> Function<Mono<T>, Mono<T>> monoLogOnCancel(Runnable runnable) {
        return new MonoMdcDecorator<>((context, mono) -> mono.doOnCancel(() -> innerRunWithMdc(context, runnable)));
    }

    public static <T> Function<Mono<T>, Mono<T>> monoLogOnRequest(LongConsumer consumer) {
        return new MonoMdcDecorator<>((context, mono) -> mono
            .doOnRequest(item -> innerRunWithMdc(context, () -> consumer.accept(item))));
    }

    public static <T> Function<Mono<T>, Mono<T>> monoLogOnTerminate(Runnable runnable) {
        return new MonoMdcDecorator<>((context, mono) -> mono.doOnTerminate(() -> innerRunWithMdc(context, runnable)));
    }

    public static <T, R> Function<Mono<T>, Mono<T>> monoLogOnDiscard(final Class<R> type,
                                                                     final Consumer<? super R> discardHook) {
        return new MonoMdcDecorator<>((context, mono) -> mono
            .doOnDiscard(type, item -> innerRunWithMdc(context, () -> discardHook.accept(item))));
    }

    public static <T> FluxMdcDecorator<T> fluxLogOnNext(Consumer<? super T> consumer) {
        return new FluxMdcDecorator<>((context, flux) -> flux
            .doOnNext(item -> innerRunWithMdc(context, () -> consumer.accept(item))));
    }

    public static <T> Function<Flux<T>, Publisher<T>> fluxLogOnSubscribe(Consumer<? super Subscription> consumer) {
        return new FluxMdcDecorator<>((context, flux) -> flux
            .doOnSubscribe(subscription -> innerRunWithMdc(context, () -> consumer.accept(subscription))));
    }

    public static <T> Function<Flux<T>, Publisher<T>> fluxLogOnError(Consumer<? super Throwable> consumer) {
        return new FluxMdcDecorator<>((context, flux) -> flux
            .doOnError(error -> innerRunWithMdc(context, () -> consumer.accept(error))));
    }

    public static <T> Function<Flux<T>, Publisher<T>> fluxLogOnComplete(Runnable runnable) {
        return new FluxMdcDecorator<>((context, flux) -> flux.doOnComplete(() -> innerRunWithMdc(context, runnable)));
    }

    public static <T> Function<Flux<T>, Publisher<T>> fluxLogOnCancel(Runnable runnable) {
        return new FluxMdcDecorator<>((context, flux) -> flux.doOnCancel(() -> innerRunWithMdc(context, runnable)));
    }

    public static <T> Function<Flux<T>, Publisher<T>> fluxLogOnRequest(LongConsumer consumer) {
        return new FluxMdcDecorator<>((context, flux) -> flux
            .doOnRequest(item -> innerRunWithMdc(context, () -> consumer.accept(item))));
    }

    public static <T> Function<Flux<T>, Publisher<T>> fluxLogOnTerminate(Runnable runnable) {
        return new FluxMdcDecorator<>((context, flux) -> flux.doOnTerminate(() -> innerRunWithMdc(context, runnable)));
    }

    public static <T, R> Function<Flux<T>, Publisher<T>> fluxLogOnDiscard(final Class<R> type,
                                                                          final Consumer<? super R> discardHook) {
        return new FluxMdcDecorator<>((context, flux) -> flux
            .doOnDiscard(type, item -> innerRunWithMdc(context, () -> discardHook.accept(item))));
    }

    @SuppressWarnings("PMD")
    private static <T> Supplier<T> innerDecorateSupplier(ContextView context, Supplier<T> supplier) {
        return () -> {
            try {
                return innerDecorateCallable(context, supplier::get).call();
            } catch (Exception e) {
                throw new ReventsException("Error happened", e);
            }
        };
    }

    private static <T> Callable<T> innerDecorateCallable(ContextView context, Callable<T> callable) {
        return () -> {
            Map<String, String> origMdc = MDC.getCopyOfContextMap();
            context.stream()
                .filter(entry -> entry.getKey() instanceof String)
                .filter(entry -> entry.getValue() instanceof String)
                .forEach(entry -> MDC.put((String) entry.getKey(), (String) entry.getValue()));
            try {
                return callable.call();
            } finally {
                if (origMdc != null) {
                    MDC.setContextMap(origMdc);
                } else {
                    MDC.clear();
                }
            }
        };
    }

    @SuppressWarnings("PMD")
    private static void innerRunWithMdc(ContextView context, Runnable runnable) {
        try {
            innerDecorateCallable(context, () -> {
                runnable.run();
                return null;
            }).call();
        } catch (Exception e) {
            throw new ReventsException("Error happened", e);
        }
    }

    public static class MonoMdcDecorator<T> implements Function<Mono<T>, Mono<T>> {

        private final BiFunction<ContextView, Mono<T>, Mono<T>> decoratorFunction;

        private MonoMdcDecorator(BiFunction<ContextView, Mono<T>, Mono<T>> decoratorFunction) {
            this.decoratorFunction = decoratorFunction;
        }

        public MonoMdcDecorator<T> logOnError(Consumer<? super Throwable> consumer) {
            return new MonoMdcDecorator<>((context, mono) -> decoratorFunction.apply(context, mono)
                .doOnError(error -> innerRunWithMdc(context, () -> consumer.accept(error))));
        }

        @Override
        public Mono<T> apply(Mono<T> mono) {
            return Mono.deferContextual(context -> decoratorFunction.apply(context, mono));
        }
    }

    public static class FluxMdcDecorator<T> implements Function<Flux<T>, Publisher<T>> {

        private final BiFunction<ContextView, Flux<T>, Flux<T>> decoratorFunction;

        private FluxMdcDecorator(BiFunction<ContextView, Flux<T>, Flux<T>> decoratorFunction) {
            this.decoratorFunction = decoratorFunction;
        }

        public FluxMdcDecorator<T> logOnError(Consumer<? super Throwable> consumer) {
            return new FluxMdcDecorator<>((context, flux) -> decoratorFunction.apply(context, flux)
                .doOnError(error -> innerRunWithMdc(context, () -> consumer.accept(error))));
        }

        public FluxMdcDecorator<T> fluxLogOnSubscribe(Consumer<? super Subscription> consumer) {
            return new FluxMdcDecorator<>((context, flux) -> decoratorFunction.apply(context, flux)
                .doOnSubscribe(subscription -> innerRunWithMdc(context, () -> consumer.accept(subscription))));
        }

        @Override
        public Publisher<T> apply(Flux<T> flux) {
            return Flux.deferContextual(context -> decoratorFunction.apply(context, flux));
        }
    }
}
