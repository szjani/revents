package com.revents.eventsourcing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.revents.AggregateId;
import com.revents.EventMessage;
import com.revents.EventMessage.EventMetaData;
import com.revents.EventStore;
import com.revents.ImmutableTestEvent1;
import com.revents.TestAggregate1;
import com.revents.TestEvent1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.OptionalLong;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class EventStoreBasedAggregateLoaderTest {

    private static final AggregateId<TestAggregate1> AN_AGGREGATE_ID = AggregateId.of(TestAggregate1.class, "abc123");

    private static final TestEvent1 AN_EVENT_PAYLOAD = ImmutableTestEvent1.builder()
        .aggregateId(UUID.randomUUID().toString())
        .payload(3)
        .build();

    private static final EventMessage<TestEvent1> A_PERSISTED_EVENT = EventMessage.<TestEvent1>builder()
        .metaData(EventMetaData.builder()
            .aggregateId(AN_AGGREGATE_ID)
            .revision(1L)
            .build())
        .payload(AN_EVENT_PAYLOAD)
        .build();

    @Mock
    EventStore eventStore;
    EventSourcedBasedAggregateLoader repository;

    @BeforeEach
    void setUp() {
        repository = EventSourcedBasedAggregateLoader.create(config -> config.eventStore(eventStore));
    }

    @Test
    void shouldLoadTestAggregate1() {
        when(eventStore.eventsForAggregate(AN_AGGREGATE_ID, OptionalLong.empty()))
            .thenReturn(Flux.just(A_PERSISTED_EVENT));
        StepVerifier.create(repository.load(AN_AGGREGATE_ID).cast(TestAggregate1.class))
            .consumeNextWith(aggregate -> assertThat(aggregate.getCommandPayload())
                .isEqualTo(AN_EVENT_PAYLOAD.payload()))
            .verifyComplete();
    }
}
