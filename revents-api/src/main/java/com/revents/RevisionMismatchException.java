package com.revents;

public class RevisionMismatchException extends ReventsException {

    private static final long serialVersionUID = -964962357912095162L;

    private final AggregateId<?> aggregateId;
    private final long expectedRevision;

    /**
     * Create a new exception about a revision mismatch.
     *
     * @param aggregateId the ID of the affected aggregate
     * @param expectedRevision the revision was expected to be in the persistent storage
     */
    public RevisionMismatchException(AggregateId<?> aggregateId, long expectedRevision) {
        super(String.format("Revision mismatch, expected %d on aggregate %s", expectedRevision, aggregateId));
        this.aggregateId = aggregateId;
        this.expectedRevision = expectedRevision;
    }

    public AggregateId<?> getAggregateId() {
        return aggregateId;
    }

    public long getExpectedRevision() {
        return expectedRevision;
    }
}
