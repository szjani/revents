package com.revents.cqrs;

import static com.revents.log.TransformContextToMdc.monoLogOnError;

import com.revents.EventHandler;
import com.revents.EventMessage;
import com.revents.MessageHandler;
import com.revents.MessageInterceptor;
import com.revents.MessageInterceptorChain;
import com.revents.cache.ClassValueBasedReactiveCache;
import com.revents.cache.ReactiveExtendedCache;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AnnotationAwareEventMessageDispatcher implements MessageHandler<EventMessage<?>, Void> {

    private static final Logger LOG = LoggerFactory.getLogger(AnnotationAwareEventMessageDispatcher.class);

    private final List<ReflectionBasedMessageHandler<EventMessage<?>, Void>> eventHandlerWrappers;

    @SuppressWarnings("checkstyle:linelength")
    private final ReactiveExtendedCache<Class<?>, List<ReflectionBasedMessageHandler<EventMessage<?>, Void>>> handlerCache =
        ReactiveExtendedCache.cacheOver(new ClassValueBasedReactiveCache<>());

    private final List<MessageInterceptor<EventMessage<?>, Void>> eventHandlerInterceptors;

    public static AnnotationAwareEventMessageDispatcher create(
        UnaryOperator<AnnotationAwareEventMessageDispatcherConfig.Builder> init) {
        return new AnnotationAwareEventMessageDispatcher(init.apply(
            new AnnotationAwareEventMessageDispatcherConfig.Builder()).build());
    }

    private AnnotationAwareEventMessageDispatcher(AnnotationAwareEventMessageDispatcherConfig config) {
        eventHandlerInterceptors = config.eventMessageHandlerInterceptors();
        eventHandlerWrappers = config.eventHandlers().stream()
            .flatMap(handlerObject -> Stream.of(handlerObject.getClass().getMethods())
                .filter(method -> method.isAnnotationPresent(EventHandler.class))
                .map(method -> ImmutableEventHandlerWrapper.builder()
                    .handlerObject(handlerObject)
                    .handlerExecutable(method)
                    .build()))
            .collect(Collectors.toList());
    }

    @Override
    public Mono<Void> handle(EventMessage<?> eventMessage) {
        return Flux.fromIterable(eventHandlerWrappers)
            .filter(eventHandlerWrapper -> eventHandlerWrapper.canHandle(eventMessage.payload()))
            .collectList()
            .as(handlerCache.withKey(eventMessage.payload().getClass()))
            .flatMapMany(Flux::fromIterable)
            .flatMap(eventHandlerWrapper ->
                MessageInterceptorChain.create(eventHandlerInterceptors, eventHandlerWrapper)
                    .handle(eventMessage)
                    .transform(monoLogOnError(e ->
                        LOG.error("Unhandled exception occurred during event processing={}", eventHandlerWrapper, e)))
                    .subscribeOn(Schedulers.parallel()))
            .then();
    }

    @Value.Immutable
    public interface AnnotationAwareEventMessageDispatcherConfig {

        List<Object> eventHandlers();

        List<MessageInterceptor<EventMessage<?>, Void>> eventMessageHandlerInterceptors();

        class Builder extends ImmutableAnnotationAwareEventMessageDispatcherConfig.Builder {
        }
    }
}
