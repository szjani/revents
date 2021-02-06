package com.revents.cqrs;

import com.revents.DynamicStream;
import com.revents.FixStream;
import com.revents.StreamName;
import org.immutables.value.Value;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class StreamNameResolverTest {

    StreamNameResolver streamNameResolver = StreamNameResolver.ANNOTATION_BASED;

    @Test
    void shouldFindAllDefinedStreamNames() {
        streamNameResolver.streamsOf(ImmutableExampleEvent.builder()
            .exampleId(ExampleId.of("foo"))
            .string("bar")
            .build())
            .as(StepVerifier::create)
            .expectNext(StreamName.of("fixStream1"))
            .expectNext(StreamName.of("fixStream2"))
            .expectNext(StreamName.of("prefix1-bar"))
            .expectNext(StreamName.of("prefix2-bar"))
            .expectNext(StreamName.of("foo"))
            .verifyComplete();
    }

    @FixStream({"fixStream1", "fixStream2"})
    @Value.Immutable
    public interface ExampleEvent {

        @DynamicStream
        ExampleId exampleId();

        @DynamicStream(prefix = {"prefix1-", "prefix2-"})
        String string();
    }

    @Value.Immutable
    public abstract static class ExampleId {

        @Value.Parameter
        public abstract String id();

        static ExampleId of(String id) {
            return ImmutableExampleId.of(id);
        }

        @Override
        public String toString() {
            return id();
        }
    }
}