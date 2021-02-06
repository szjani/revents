package com.revents.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class SerializedTypeMapperTest {

    static final ImmutableClass1 CLASS_1_INSTANCE = ImmutableClass1.of("one");
    static final ImmutableClass2 CLASS_2_INSTANCE = ImmutableClass2.of("two");
    static final ImmutableClass3 CLASS_3_INSTANCE = ImmutableClass3.of("three");

    SerializedTypeMapper typeMapper;
    Serializer serializer;

    @BeforeEach
    void setUp() {
        typeMapper = SerializedTypeMapper.scanSerializedTypeAnnotatedClassesIn("com.revents.serialization")
            .fallbackTo(SerializedTypeMapper.CLASS_NAME_BASED);
        serializer = Serializer.defaultJackson();
    }

    @ParameterizedTest
    @MethodSource("expectedClassToStringMappings")
    <T> void shouldMapClassToType(Class<T> clazz, String type) throws JsonProcessingException {
        assertThat(typeMapper.classToString(clazz)).hasValue(type);
    }

    static Stream<Arguments> expectedClassToStringMappings() {
        return Stream.of(
            Arguments.of(Class1.class, "class1"),
            Arguments.of(ImmutableClass1.class, "class1"),
            Arguments.of(Class3.class, "com.revents.serialization.SerializedTypeMapperTest$Class3"),
            Arguments.of(ImmutableClass3.class, "com.revents.serialization.SerializedTypeMapperTest$Class3"),
            Arguments.of(Class2.class, "com.revents.serialization.SerializedTypeMapperTest$Class2"),
            Arguments.of(ImmutableClass2.class, "com.revents.serialization.ImmutableClass2"));
    }

    @ParameterizedTest
    @MethodSource("expectedStringToClassMappings")
    <T> void shouldMapTypeToClass(String type, Class<T> clazz) {
        assertThat(typeMapper.<T>stringToClass(type)).hasValue(clazz);
    }

    static Stream<Arguments> expectedStringToClassMappings() {
        return Stream.of(
            Arguments.of("class1", Class1.class),
            Arguments.of("com.revents.serialization.SerializedTypeMapperTest$Class3", Class3.class),
            Arguments.of("com.revents.serialization.SerializedTypeMapperTest$Class2", Class2.class),
            Arguments.of("com.revents.serialization.ImmutableClass2", ImmutableClass2.class));
    }

    @ParameterizedTest
    @MethodSource("serializationParameters")
    <T> void shouldSerializationWork(T inputObject, String expectedType) {
        Optional<String> serializedType = typeMapper.classToString(inputObject.getClass());
        assertThat(serializedType).hasValue(expectedType);
        String serialized = serializer.serialize(inputObject);
        assertThat(serializer.deserialize(serialized, typeMapper.stringToClass(expectedType).get()))
            .isEqualTo(inputObject);
    }

    static Stream<Arguments> serializationParameters() {
        return Stream.of(
            Arguments.of(CLASS_1_INSTANCE, "class1"),
            Arguments.of(CLASS_3_INSTANCE, "com.revents.serialization.SerializedTypeMapperTest$Class3"),
            Arguments.of(CLASS_2_INSTANCE, "com.revents.serialization.ImmutableClass2"));
    }

    @Test
    void shouldEmitErrorIfSerializedTypeClassIsNotSuperClass() {
        assertThatThrownBy(() -> typeMapper.classToString(Class4.class))
            .hasMessage("Class defined in @SerializedType on interface "
                + "com.revents.serialization.SerializedTypeMapperTest$Class4 must be its supertype");
    }

    @SerializedType(asString = "class1")
    @Value.Immutable
    @JsonSerialize(as = ImmutableClass1.class)
    @JsonDeserialize(as = ImmutableClass1.class)
    interface Class1 {

        @Value.Parameter
        String value();
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableClass2.class)
    @JsonDeserialize(as = ImmutableClass2.class)
    interface Class2 {

        @Value.Parameter
        String value();
    }

    @SerializedType(asSuper = Class3.class)
    @Value.Immutable
    @JsonSerialize(as = ImmutableClass3.class)
    @JsonDeserialize(as = ImmutableClass3.class)
    interface Class3 {

        @Value.Parameter
        String value();
    }

    @SerializedType(asSuper = String.class)
    @Value.Immutable
    interface Class4 {
    }
}