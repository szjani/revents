package com.revents.springboot.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@SuppressWarnings("PMD.DataClass")
@ConfigurationProperties(prefix = "revents")
public class ReventsSpringProperties {

    private final EventProcessorManager eventProcessorManager = new EventProcessorManager();
    private final R2dbc r2dbc = new R2dbc();
    private final Mongo mongo = new Mongo();
    private String scanPackage;

    public String getScanPackage() {
        return scanPackage;
    }

    public void setScanPackage(String scanPackage) {
        this.scanPackage = scanPackage;
    }

    public R2dbc getR2dbc() {
        return r2dbc;
    }

    public Mongo getMongo() {
        return mongo;
    }

    public EventProcessorManager getEventProcessorManager() {
        return eventProcessorManager;
    }

    public static class EventProcessorManager {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Mongo {

        private final TokenStore tokenStore = new TokenStore();

        public TokenStore getTokenStore() {
            return tokenStore;
        }

        public static class TokenStore {

            private boolean enabled;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }

    public static class R2dbc {

        private final EventStore eventStore = new EventStore();
        private final SnapshotRepository snapshotRepository = new SnapshotRepository();

        public EventStore getEventStore() {
            return eventStore;
        }

        public SnapshotRepository getSnapshotRepository() {
            return snapshotRepository;
        }

        public static class SnapshotRepository {

            private boolean enabled;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class EventStore {

            private boolean enabled;

            private int eventStreamBucketSize = 100;

            private Duration eventStreamFetchRepeat = Duration.ofSeconds(5);

            public int getEventStreamBucketSize() {
                return eventStreamBucketSize;
            }

            public void setEventStreamBucketSize(int eventStreamBucketSize) {
                this.eventStreamBucketSize = eventStreamBucketSize;
            }

            public Duration getEventStreamFetchRepeat() {
                return eventStreamFetchRepeat;
            }

            public void setEventStreamFetchRepeat(Duration eventStreamFetchRepeat) {
                this.eventStreamFetchRepeat = eventStreamFetchRepeat;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }

}
