package com.revents.eventstore;

import com.revents.EventMessage.EventId;
import com.revents.EventProcessor.EventProcessorId;
import com.revents.EventStreamPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

public abstract class TokenStoreContract {

    protected abstract TokenStore createTokenStore();

    TokenStore tokenStore;

    @BeforeEach
    void setUp() {
        Hooks.onOperatorDebug();
        tokenStore = createTokenStore();
    }

    @Test
    void shouldReturnBeginningIfNoTokenFound() {
        StepVerifier.create(tokenStore.tokenFor(EventProcessorId.of("any")))
            .expectNext(EventStreamPosition.BEGINNING);
    }

    @Nested
    class AfterTokenIsSaved {

        EventProcessorId processorId1 = EventProcessorId.of("processorId1");
        EventId eventId1 = EventId.of("eventId1");

        @BeforeEach
        void setUp() {
            tokenStore.save(processorId1, EventStreamPosition.of(eventId1)).block();
        }

        @Test
        void shouldReturnSavedToken() {
            StepVerifier.create(tokenStore.tokenFor(processorId1))
                .expectNext(EventStreamPosition.of(eventId1))
                .verifyComplete();
        }

        @Test
        void shouldReturnBeginningForAnotherConsumer() {
            StepVerifier.create(tokenStore.tokenFor(EventProcessorId.of("any")))
                .expectNext(EventStreamPosition.BEGINNING)
                .verifyComplete();
        }

        @Nested
        class AfterTokenIsUpdated {

            EventId eventId2 = EventId.of("eventId2");

            @BeforeEach
            void setUp() {
                tokenStore.save(processorId1, EventStreamPosition.of(eventId2)).block();
            }

            @Test
            void shouldReturnNewToken() {
                StepVerifier.create(tokenStore.tokenFor(processorId1))
                    .expectNext(EventStreamPosition.of(eventId2))
                    .verifyComplete();
            }
        }

    }
}
