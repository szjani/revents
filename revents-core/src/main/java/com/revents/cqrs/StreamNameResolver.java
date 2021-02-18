package com.revents.cqrs;

import static com.revents.log.TransformContextToMdc.fluxLogOnNext;
import static org.reflections.ReflectionUtils.getAllAnnotations;
import static org.reflections.ReflectionUtils.getAllMethods;
import static org.reflections.ReflectionUtils.withAnnotation;
import static org.reflections.ReflectionUtils.withParametersCount;

import com.revents.DynamicStream;
import com.revents.FixStream;
import com.revents.NotFollowedReventsConventionException;
import com.revents.StreamName;
import com.revents.cache.ClassValueBasedReactiveCache;
import com.revents.cache.ReactiveExtendedCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public interface StreamNameResolver {

    StreamNameResolver ANNOTATION_BASED = new AnnotationBased();

    <E> Flux<StreamName> streamsOf(E event);

    class AnnotationBased implements StreamNameResolver {

        private static final Logger LOG = LoggerFactory.getLogger(AnnotationBased.class);

        private final ReactiveExtendedCache<Class<?>, List<String>> fixStreamCache =
            ReactiveExtendedCache.cacheOver(new ClassValueBasedReactiveCache<>());

        private final ReactiveExtendedCache<Class<?>, List<Tuple2<Method, List<String>>>> dynamicStreamCache =
            ReactiveExtendedCache.cacheOver(new ClassValueBasedReactiveCache<>());

        @SuppressWarnings("unchecked")
        @Override
        public <E> Flux<StreamName> streamsOf(E event) {
            return Flux.defer(() -> Flux.fromIterable(getAllAnnotations(event.getClass())))
                .filter(FixStream.class::isInstance)
                .cast(FixStream.class)
                .map(FixStream::value)
                .flatMap(Flux::fromArray)
                .collectList()
                .as(fixStreamCache.withKey(event.getClass()))
                .flatMapMany(Flux::fromIterable)
                .concatWith(Flux.defer(() -> Flux.fromIterable(getAllMethods(event.getClass(),
                        withAnnotation(DynamicStream.class),
                        withParametersCount(0))))
                    .doOnNext(method -> method.setAccessible(true))
                    .map(method -> Tuples.of(method, List.of(method.getAnnotation(DynamicStream.class).prefix())))
                    .collectList()
                    .as(dynamicStreamCache.withKey(event.getClass()))
                    .flatMapMany(Flux::fromIterable)
                    .map(tuple -> {
                        try {
                            return Tuples.of(tuple.getT1().invoke(event), tuple.getT2());
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new NotFollowedReventsConventionException("@DynamicStream " + tuple.getT1()
                                + " method cannot be called on event " + event, e);
                        }
                    })
                    .flatMap(tuple -> Flux.fromIterable(tuple.getT2()).map(prefix -> prefix + tuple.getT1())))
                .transform(fluxLogOnNext(name -> LOG.debug("Stream name '{}' defined for event: {}", name, event)))
                .map(StreamName::of);
        }
    }
}
