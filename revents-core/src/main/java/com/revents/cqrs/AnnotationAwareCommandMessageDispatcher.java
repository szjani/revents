package com.revents.cqrs;

import static com.revents.log.TransformContextToMdc.fluxLogOnSubscribe;
import static org.reflections.ReflectionUtils.getAllConstructors;
import static org.reflections.ReflectionUtils.getAllMethods;
import static org.reflections.ReflectionUtils.withAnnotation;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.revents.Aggregate;
import com.revents.AggregateId;
import com.revents.AggregateLoader;
import com.revents.CommandHandler;
import com.revents.CommandMessage;
import com.revents.CommandResult;
import com.revents.MessageHandler;
import com.revents.NotFollowedReventsConventionException;
import com.revents.TargetAggregate;
import com.revents.cache.ClassValueBasedReactiveCache;
import com.revents.cache.ReactiveExtendedCache;
import org.immutables.value.Value;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("PMD.ExcessiveImports")
public class AnnotationAwareCommandMessageDispatcher implements
    MessageHandler<CommandMessage<?>, CommandResult>,
    CommandAggregateIdResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AnnotationAwareCommandMessageDispatcher.class);

    private final List<ReflectionBasedMessageHandler<CommandMessage<?>, CommandResult>> commandHandlerWrappers;

    @SuppressWarnings("checkstyle:linelength")
    private final ReactiveExtendedCache<Class<?>, List<ReflectionBasedMessageHandler<CommandMessage<?>, CommandResult>>> handlerCache =
        ReactiveExtendedCache.cacheOver(new ClassValueBasedReactiveCache<>());

    private final ReactiveExtendedCache<Class<?>, List<Method>> targetAggregateCache =
        ReactiveExtendedCache.cacheOver(new ClassValueBasedReactiveCache<>());

    public static AnnotationAwareCommandMessageDispatcher create(
        UnaryOperator<AnnotationAwareCommandMessageDispatcherConfig.Builder> init) {
        return new AnnotationAwareCommandMessageDispatcher(init.apply(
            new AnnotationAwareCommandMessageDispatcherConfig.Builder()).build());
    }

    @SuppressWarnings("unchecked")
    private AnnotationAwareCommandMessageDispatcher(AnnotationAwareCommandMessageDispatcherConfig config) {
        commandHandlerWrappers = config.aggregateCommandHandlers().stream()
            .flatMap(handlerClass -> Stream.concat(
                getAllMethods(handlerClass, withAnnotation(CommandHandler.class)).stream(),
                getAllConstructors(handlerClass, withAnnotation(CommandHandler.class)).stream()))
            .map(executable -> ImmutableAggregateCommandHandlerWrapper.builder()
                .aggregateLoader(config.aggregateLoader())
                .handlerExecutable(executable)
                .build())
            .collect(Collectors.toList());
    }

    @Override
    public Mono<CommandResult> handle(CommandMessage<?> commandMessage) {
        return wrapperFor(commandMessage.payload())
            .flatMap(wrapper -> wrapper.handle(commandMessage));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C> Mono<AggregateId<?>> aggregateIdOf(C command) {
        return Flux.defer(() -> Flux.fromIterable(ReflectionUtils.getAllMethods(
                command.getClass(),
                ReflectionUtils.withAnnotation(TargetAggregate.class))))
            .transform(fluxLogOnSubscribe(x -> LOG.debug("Searching @TargetAggregate methods in={}", command)))
            .doOnNext(method -> method.setAccessible(true))
            .collectList()
            .as(targetAggregateCache.withKey(command.getClass()))
            .flatMapMany(Flux::fromIterable)
            .singleOrEmpty()
            .map(method -> {
                try {
                    return method.invoke(command).toString();
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new NotFollowedReventsConventionException("@TargetAggregate " + method + " method "
                        + "cannot be called on command " + command, e);
                }
            })
            .flatMap(aggregateStringId -> wrapperFor(command)
                .map(ReflectionBasedMessageHandler::handlerExecutable)
                .map(Executable::getDeclaringClass)
                .map(aggregateType -> AggregateId.of(aggregateType, aggregateStringId)));
    }

    private <T> Mono<ReflectionBasedMessageHandler<CommandMessage<?>, CommandResult>> wrapperFor(T command) {
        return Flux.fromIterable(commandHandlerWrappers)
            .transform(fluxLogOnSubscribe(x -> LOG.debug("Searching command handler for={}", command)))
            .filter(wrapper -> wrapper.canHandle(command))
            .collectList()
            .as(handlerCache.withKey(command.getClass()))
            .flatMapMany(Flux::fromIterable)
            .single()
            .onErrorMap(IndexOutOfBoundsException.class, e ->
                new NotFollowedReventsConventionException("Multiple command handlers found for command " + command))
            .onErrorMap(NoSuchElementException.class, e ->
                new NotFollowedReventsConventionException("No handler method found for command " + command));
    }

    @Value.Immutable
    public interface AnnotationAwareCommandMessageDispatcherConfig {

        AggregateLoader aggregateLoader();

        List<Class<?>> aggregateCommandHandlers();

        List<Object> externalCommandHandlers(); // TODO support external command handlers

        class Builder extends ImmutableAnnotationAwareCommandMessageDispatcherConfig.Builder {

            @CanIgnoreReturnValue
            public Builder scanAggregateClassesIn(String packageName) {
                Reflections reflections = new Reflections(packageName);
                Set<Class<?>> aggregateClasses = reflections.getTypesAnnotatedWith(Aggregate.class);
                Preconditions.checkState(!aggregateClasses.isEmpty(),
                    "No @Aggregate classes found in package " + packageName);
                LOG.info("Found @Aggregate annotated classes={}", aggregateClasses);
                aggregateCommandHandlers(aggregateClasses);
                return this;
            }
        }
    }
}
