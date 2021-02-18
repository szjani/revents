package com.revents.cqrs;

import static com.revents.log.TransformContextToMdc.fluxLogOnSubscribe;

import com.revents.ExpectedRevision;
import com.revents.NotFollowedReventsConventionException;
import com.revents.cache.ClassValueBasedReactiveCache;
import com.revents.cache.ReactiveExtendedCache;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public interface CommandRevisionResolver {

    CommandRevisionResolver ANNOTATION_BASED = new AnnotationBased();

    <C> Mono<Long> revisionOf(C command);

    class AnnotationBased implements CommandRevisionResolver {

        private static final Logger LOG = LoggerFactory.getLogger(AnnotationBased.class);

        private final ReactiveExtendedCache<Class<?>, List<Method>> revisionMethodCache =
            ReactiveExtendedCache.cacheOver(new ClassValueBasedReactiveCache<>());

        @SuppressWarnings("unchecked")
        @Override
        public <C> Mono<Long> revisionOf(C command) {
            return Flux.defer(() -> Flux.fromIterable(ReflectionUtils.getAllMethods(
                    command.getClass(),
                    ReflectionUtils.withAnnotation(ExpectedRevision.class),
                    ReflectionUtils.withParametersCount(0))))
                .transform(fluxLogOnSubscribe(x ->
                    LOG.debug("Searching @ExpectedRevision methods in={}", command)))
                .doOnNext(method -> method.setAccessible(true))
                .collectList()
                .as(revisionMethodCache.withKey(command.getClass()))
                .flatMapMany(Flux::fromIterable)
                .singleOrEmpty()
                .map(method -> {
                    try {
                        return method.invoke(command);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new NotFollowedReventsConventionException("@ExpectedRevision " + method + " method "
                            + "cannot be called on command " + command, e);
                    }
                })
                .filter(Number.class::isInstance)
                .cast(Number.class)
                .map(Number::longValue);
        }
    }
}
