package com.revents.eventstore;

import com.revents.EventStore;
import com.revents.EventStoreContract;

class InMemoryEventStoreTest extends EventStoreContract {

    @Override
    protected EventStore createEventStore() {
        return new InMemoryEventStore(new InMemoryTokenStore());
    }
}
