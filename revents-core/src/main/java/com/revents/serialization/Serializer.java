package com.revents.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.revents.ReventsException;
import org.immutables.value.Value;

public interface Serializer {

    /**
     * Create a default Jackson-based serializer.
     *
     * @return a new serializer
     */
    static Serializer defaultJackson() {
        return jackson(new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .registerModule(new GuavaModule())
            .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE));
    }

    static Serializer jackson(ObjectMapper objectMapper) {
        return ImmutableJacksonSerializer.of(objectMapper);
    }

    <T> String serialize(T object);

    <T> T deserialize(String object, Class<T> type);

    @Value.Immutable
    interface JacksonSerializer extends Serializer {

        @Value.Parameter
        ObjectMapper objectMapper();

        @Override
        default <T> String serialize(T object) {
            try {
                return objectMapper().writeValueAsString(object);
            } catch (JsonProcessingException e) {
                throw new ReventsException("Serialization error happened", e);
            }
        }

        @Override
        default <T> T deserialize(String object, Class<T> type) {
            try {
                return objectMapper().readValue(object, type);
            } catch (JsonProcessingException e) {
                throw new ReventsException("Deserialization error happened", e);
            }
        }
    }
}
