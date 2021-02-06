package com.revents.cqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.validator.testutil.ConstraintViolationAssert.pathWith;

import com.revents.AggregateId;
import com.revents.EventMessage;
import com.revents.Message;
import com.revents.MessageInterceptorChain;
import com.revents.TestAggregate1;
import com.revents.validation.FallbackGetterPropertySelectionStrategy;
import com.revents.validation.ValidationMessageInterceptor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.testutil.ConstraintViolationAssert;
import org.immutables.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.constraints.Positive;
import java.util.List;
import java.util.stream.Stream;

class ValidationMessageInterceptorTest {

    ValidationMessageInterceptor<Message<?, ?, ?>, String> interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ValidationMessageInterceptor<>(Validation.byProvider(HibernateValidator.class)
            .configure()
            .getterPropertySelectionStrategy(new FallbackGetterPropertySelectionStrategy())
            .buildValidatorFactory()
            .getValidator());
    }

    static Stream<Object> validPayload() {
        return Stream.of(Payload.of(1), new Pojo(1));
    }

    @ParameterizedTest
    @MethodSource("validPayload")
    void shouldValidateValidMessageAndReturn(Object payload) {
        MessageInterceptorChain.create(List.of(interceptor), message -> Mono.just("foo"))
            .handle(createEventMessage(payload))
            .as(StepVerifier::create)
            .expectNext("foo")
            .verifyComplete();
    }

    static Stream<Object> invalidPayload() {
        return Stream.of(Payload.of(-1), new Pojo(-1));
    }

    @ParameterizedTest
    @MethodSource("invalidPayload")
    void shouldThrowConstraintViolationException(Object payload) {
        MessageInterceptorChain.create(List.of(interceptor), message -> Mono.just("foo"))
            .handle(createEventMessage(payload))
            .as(StepVerifier::create)
            .verifyErrorSatisfies(e -> assertThat(e)
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessage("Message is not valid")
                .asInstanceOf(InstanceOfAssertFactories.type(ConstraintViolationException.class))
                .satisfies(violationException ->
                    ConstraintViolationAssert.assertThat(violationException.getConstraintViolations())
                        .containsPaths(pathWith().property("positive"))));
    }

    private <T> EventMessage<T> createEventMessage(T payload) {
        return EventMessage.<T>builder()
            .metaData(EventMessage.EventMetaData.builder()
                .aggregateId(AggregateId.of(TestAggregate1.class, "1"))
                .build())
            .payload(payload)
            .build();
    }

    @Value.Immutable
    interface Payload {

        @Value.Parameter
        @Positive
        int positive();

        static Payload of(int number) {
            return ImmutablePayload.of(number);
        }
    }

    static class Pojo {

        @Positive
        private int positive;

        public Pojo(int positive) {
            this.positive = positive;
        }

        public int getPositive() {
            return positive;
        }

        public void setPositive(int positive) {
            this.positive = positive;
        }
    }
}
