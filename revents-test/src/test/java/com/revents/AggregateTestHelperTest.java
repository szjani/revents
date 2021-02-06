package com.revents;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@ExtendWith(MockitoExtension.class)
class AggregateTestHelperTest {

    String aggregateId = "expectedId";
    TestCommand1 firstCommand;
    TestCommand2 secondCommand;
    int firstCommandPayload;
    int secondCommandPayload;
    static AggregateTestHelper aggregateTestHelper = AggregateTestHelper.testing(TestAggregate1.class);

    @BeforeEach
    void setUp() {
        firstCommandPayload = 10;
        firstCommand = ImmutableTestCommand1.builder()
            .useThisId(aggregateId)
            .payload(firstCommandPayload)
            .build();
        secondCommandPayload = 5;
        secondCommand = ImmutableTestCommand2.builder()
            .id(aggregateId)
            .payload(secondCommandPayload)
            .build();
    }

    @AfterEach
    void tearDown() {
        ReventsClock.reset();
    }

    @Test
    void shouldHandleFirstCommand() {
        aggregateTestHelper
            .given()
            .when(firstCommand)
            .thenExpect(events -> assertThat(events)
                .hasSize(1)
                .element(0)
                .isInstanceOf(TestEvent1.class));
    }

    @Test
    void shouldHandleSecondCommand() {
        TestEvent1 firstEvent = aggregateTestHelper
            .given()
            .when(firstCommand)
            .thenReturnSingle(TestEvent1.class);

        aggregateTestHelper
            .given(firstEvent)
            .when(secondCommand)
            .thenExpectSingle(TestEvent2.class, event -> assertThat(event.payload()).isEqualTo(secondCommandPayload));
    }

    @Test
    void shouldHandleMultipleEventsDuringLoad() {
        aggregateTestHelper
            .given(ImmutableTestEvent1.builder()
                .aggregateId(aggregateId)
                .payload(firstCommand.payload())
                .build())
            .andGiven(ImmutableTestEvent2.builder()
                .payload(secondCommand.payload())
                .build())
            .when(ImmutableTestCommand3.builder()
                .id(aggregateId)
                .payload(1)
                .revision(2)
                .build())
            .thenExpect(events -> assertThat(events)
                .hasSize(1)
                .element(0)
                .isInstanceOf(TestEvent3.class));
    }

    @Test
    void shouldHandleError() {
        TestEvent1 firstEvent = aggregateTestHelper
            .given()
            .when(firstCommand)
            .thenReturnSingle(TestEvent1.class);

        aggregateTestHelper
            .given(firstEvent)
            .when(ImmutableTestCommand2.builder()
                .id(aggregateId)
                .payload(3)
                .build())
            .thenExpectError(error -> assertThat(error)
                .hasMessage("10 cannot be divided by 3"));
    }

    @Test
    void shouldHandleExplicitClock() {
        ZoneId euBud = ZoneId.of("Europe/Budapest");
        ReventsClock.overrideSystemClock(Clock.system(euBud));
        ZonedDateTime expectedZonedDateTime = ZonedDateTime.parse("2020-01-01T10:00:00Z").withZoneSameInstant(euBud);

        TestEvent1 firstEvent = aggregateTestHelper
            .given()
            .when(firstCommand)
            .thenReturnSingle(TestEvent1.class);

        aggregateTestHelper
            .given(firstEvent)
            .andGivenCurrentTime(expectedZonedDateTime.toInstant())
            .when(ImmutableTestCommand3.builder()
                .id(aggregateId)
                .payload(3)
                .revision(1)
                .build())
            .thenExpectSingle(TestEvent3.class, event -> assertThat(event.created())
                .satisfies(created -> assertThat(created.getZone()).isEqualTo(expectedZonedDateTime.getZone()))
                .isEqualTo(expectedZonedDateTime));
    }
}