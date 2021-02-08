package com.revents.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.revents.AggregateId;
import com.revents.CommandMessage;
import com.revents.EventMessage;
import org.immutables.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageSerializationTest {

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .registerModule(new GuavaModule())
            .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    @Test
    void shouldSerializeAndDeserializeCommand() throws JsonProcessingException {
        CommandMessage<TestCommand1> commandMessage = CommandMessage.<TestCommand1>builder()
            .metaData(CommandMessage.CommandMetaData.builder()
                .aggregateId(AggregateId.of(TestAggregate1.class, "id1"))
                .revision(2L)
                .build())
            .payload(ImmutableTestCommand1.of("command-value"))
            .build();

        String json = objectMapper.writeValueAsString(commandMessage);
        CommandMessage<?> deserialized = objectMapper.readValue(json, CommandMessage.class);

        assertThat(deserialized).isEqualTo(commandMessage);
    }

    @Test
    void shouldSerializeAndDeserializeEvent() throws JsonProcessingException {
        EventMessage<TestEvent1> eventMessage = EventMessage.<TestEvent1>builder()
            .metaData(EventMessage.EventMetaData.builder()
                .aggregateId(AggregateId.of(TestAggregate1.class, "id1"))
                .revision(2L)
                .build())
            .payload(ImmutableTestEvent1.of("event-value"))
            .build();

        String json = objectMapper.writeValueAsString(eventMessage);
        EventMessage<?> deserialized = objectMapper.readValue(json, EventMessage.class);

        assertThat(deserialized).isEqualTo(eventMessage);
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableTestCommand1.class)
    @JsonDeserialize(as = ImmutableTestCommand1.class)
    interface TestCommand1 {

        @Value.Parameter
        String value();
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableTestEvent1.class)
    @JsonDeserialize(as = ImmutableTestEvent1.class)
    interface TestEvent1 {

        @Value.Parameter
        String value();
    }

    static final class TestAggregate1 {
    }
}
