package com.revents.eventstore;

class InMemoryTokenStoreTest extends TokenStoreContract {

    @Override
    protected TokenStore createTokenStore() {
        return new InMemoryTokenStore();
    }
}
