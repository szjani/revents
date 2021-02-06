package com.revents.serialization;

import static java.util.function.Predicate.not;
import static org.reflections.ReflectionUtils.getAllAnnotations;

import com.revents.NotFollowedReventsConventionException;
import com.revents.ReventsException;
import org.reflections.Reflections;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public interface SerializedTypeMapper {

    SerializedTypeMapper CLASS_NAME_BASED = new ClassNameBased();

    static SerializedTypeMapper scanSerializedTypeAnnotatedClassesIn(String packageName) {
        return new AnnotationBased(packageName);
    }

    <T> Optional<String> classToString(Class<T> clazz);

    <T> Optional<Class<T>> stringToClass(String serializedType);

    default <T> String classToStringEagerly(Class<T> clazz) {
        return classToString(clazz).orElseThrow(() -> new ReventsException("No mapping type for class: " + clazz));
    }

    default <T> Class<T> stringToClassEagerly(String serializedType) {
        return this.<T>stringToClass(serializedType)
            .orElseThrow(() -> new ReventsException("No mapping class for type: " + serializedType));
    }

    /**
     * Fallback to the given mapper if a method call on this mapper returns {@code Optional.empty()}.
     *
     * @param otherMapper the other mapper
     * @return a fallbacking mapper
     */
    default SerializedTypeMapper fallbackTo(SerializedTypeMapper otherMapper) {
        return new SerializedTypeMapper() {

            @Override
            public <T> Optional<String> classToString(Class<T> clazz) {
                return SerializedTypeMapper.this.classToString(clazz)
                    .or(() -> otherMapper.classToString(clazz));
            }

            @Override
            public <T> Optional<Class<T>> stringToClass(String serializedType) {
                return SerializedTypeMapper.this.<T>stringToClass(serializedType)
                    .or(() -> otherMapper.stringToClass(serializedType));
            }
        };
    }

    class ClassNameBased implements SerializedTypeMapper {

        @Override
        public <T> Optional<String> classToString(Class<T> clazz) {
            return Optional.of(clazz.getName());
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<Class<T>> stringToClass(String serializedType) {
            return Optional.of(serializedType)
                .flatMap(type -> {
                    Optional<Class<T>> result;
                    try {
                        result = Optional.of((Class<T>) Class.forName(serializedType));
                    } catch (ClassNotFoundException e) {
                        result = Optional.empty();
                    }
                    return result;
                });
        }
    }

    class AnnotationBased implements SerializedTypeMapper {

        private final Map<String, Class<?>> typeToClassMap;
        private final Map<Class<?>, Optional<String>> classToTypeCache = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        private AnnotationBased(String packageName) {
            var reflections = new Reflections(packageName);
            typeToClassMap = reflections.getTypesAnnotatedWith(SerializedType.class, true).stream()
                .flatMap(clazz -> getAllAnnotations(clazz).stream()
                    .filter(SerializedType.class::isInstance)
                    .map(SerializedType.class::cast)
                    .flatMap(serializedType -> Optional.of(serializedType.asString())
                        .filter(not(String::isBlank))
                        .or(() -> Optional.of(serializedType.asSuper())
                            .map(Class::getName))
                        .stream())
                    .findFirst()
                    .map(value -> Tuples.<String, Class<?>>of(value, clazz))
                    .stream())
                .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<String> classToString(Class<T> clazz) {
            return classToTypeCache.computeIfAbsent(clazz, unCachedClass -> getAllAnnotations(clazz).stream()
                .filter(SerializedType.class::isInstance)
                .map(SerializedType.class::cast)
                .map(serializedType -> Optional.of(serializedType.asString())
                    .filter(not(String::isBlank))
                    .orElseGet(() -> Optional.of(serializedType.asSuper())
                        .filter(superClass -> superClass.isAssignableFrom(clazz))
                        .map(Class::getName)
                        .orElseThrow(() -> new NotFollowedReventsConventionException(
                            "Class defined in @SerializedType on " + clazz + " must be its supertype"))))
                .findFirst());
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<Class<T>> stringToClass(String serializedType) {
            return Optional.ofNullable((Class<T>) typeToClassMap.get(serializedType));
        }
    }
}
