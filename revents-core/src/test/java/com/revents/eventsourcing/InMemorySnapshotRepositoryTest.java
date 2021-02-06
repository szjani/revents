package com.revents.eventsourcing;

class InMemorySnapshotRepositoryTest extends SnapshotRepositoryContract {

    @Override
    protected SnapshotRepository createSnapshotRepository() {
        return new InMemorySnapshotRepository();
    }
}
