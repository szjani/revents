package com.revents;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.OptionalLong;

public interface EventStore {

    /**
     * Saves all the given event messages.
     *
     * <p>
     *     If {@code originatingRevision} is not empty then it must be the same as the revision of the latest event
     *     stored for the given aggregate. In case of a mismatch {@link RevisionMismatchException} exception is emitted.
     * </p>
     *
     * @param aggregateId the event messages are related to
     * @param eventMessages need to be stored
     * @param originatingRevision set to prevent concurrency issues
     * @return the persisted events containing the sequence number
     */
    Mono<List<EventMessage<?>>> store(AggregateId<?> aggregateId,
                                      List<EventMessage<?>> eventMessages,
                                      OptionalLong originatingRevision);

    /**
     * Read events from the requested stream.
     *
     * <p>
     *     Event message acknowledge can be used only if {@code EventProcessorId} is given.
     *     In that case the event consumption can be continued from the last acknowledged
     *     message even if the process stopped.
     * </p>
     *
     * <p>
     *     If {@code infinite} subscription is requested then the returned {@code Flux}
     *     will never complete, new events stored by any clients of the event store
     *     will be also emitted.
     * </p>
     *
     * @param streamReadRequest the request parameters
     * @return an event stream subscription
     */
    EventStreamSubscription readEventStream(StreamReadRequest streamReadRequest);

    /**
     * Finds all the events for a certain aggregate instance.
     *
     * @param aggregateId the ID of the aggregate
     * @param afterRevision if set then the returned events' revision will start from afterRevision + 1,
     *                      otherwise all the related events will be returned
     * @return the related events
     */
    Flux<EventMessage<?>> eventsForAggregate(AggregateId<?> aggregateId, OptionalLong afterRevision);
}
