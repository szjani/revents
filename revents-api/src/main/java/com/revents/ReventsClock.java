package com.revents;

import static java.util.Objects.requireNonNull;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.ZoneId;
import java.util.function.Function;

public final class ReventsClock {

    private static final Clock defaultClock = Clock.system(ZoneId.of("UTC"));
    private static Clock systemClock = defaultClock;

    private ReventsClock() {
    }

    /**
     * This clock is used for all date/time calculation in revents.
     *
     * @return the clock used by Revents
     */
    public static Clock system() {
        return systemClock;
    }

    /**
     * Globally override the system clock.
     *
     * @param clock the new clock
     */
    public static void overrideSystemClock(Clock clock) {
        systemClock = requireNonNull(clock);
    }

    /**
     * Resets the Revents system clock to the default one.
     */
    public static void reset() {
        systemClock = defaultClock;
    }

    /**
     * Operator that puts the system clock into the context
     * if there is no clock stored there yet.
     *
     * @param <T> type of the stream
     * @return a function to pass {@link Mono#as(Function)}
     */
    public static <T> Function<Mono<T>, Mono<T>> monoContextualClock() {
        return mono -> mono.contextWrite(context -> context.putNonNull(Clock.class,
            context.getOrDefault(Clock.class, ReventsClock.system())));
    }

    /**
     * Operator that puts the system clock into the context
     * if there is no clock stored there yet.
     *
     * @param <T> type of the stream
     * @return a function to pass {@link Flux#as(Function)}
     */
    public static <T> Function<Flux<T>, Flux<T>> fluxContextualClock() {
        return flux -> flux.contextWrite(context -> context.putNonNull(Clock.class,
            context.getOrDefault(Clock.class, ReventsClock.system())));
    }
}
