package com.revents.eventstore;

import com.mongodb.BasicDBList;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongoCmdOptions;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import org.bson.Document;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Configuration
public class MongoTestDatabaseConfiguration {

    private static final String REPLICA_SET_NAME = "revents";

    private int embeddedPort;

    @Bean
    MongoDatabase mongoDatabase(MongoClient mongoClient) {
        return mongoClient.getDatabase("test");
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    MongodExecutable mongodExecutable() throws IOException {
        MongodStarter starter = MongodStarter.getDefaultInstance();
        embeddedPort = Network.getFreeServerPort();
        MongodConfig mongodConfig = MongodConfig.builder()
            .version(Version.Main.V4_0)
            .putArgs("--replSet", REPLICA_SET_NAME)
            .cmdOptions(MongoCmdOptions.builder().useNoJournal(false).build())
            .stopTimeoutInMillis(10000)
            .net(new Net(embeddedPort, Network.localhostIsIPv6())).build();
        return starter.prepare(mongodConfig);
    }

    @Bean(destroyMethod = "close")
    MongoClient embeddedMongoClient(MongodExecutable mongodExecutable) {
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString("mongodb://localhost:" + embeddedPort))
            .applyToClusterSettings(block -> block.mode(ClusterConnectionMode.MULTIPLE))
            .retryWrites(false)
            .build();
        return MongoClients.create(settings);
    }

    @Bean
    EmbeddedMongoReplicaSetInitialization embeddedMongoReplicaSetInitialization() {
        return new EmbeddedMongoReplicaSetInitialization();
    }

    class EmbeddedMongoReplicaSetInitialization {

        EmbeddedMongoReplicaSetInitialization() {
            MongoClient mongoClient = null;
            try {
                final BasicDBList members = new BasicDBList();
                members.add(new Document("_id", 0).append("host", "localhost:" + embeddedPort));

                final Document replSetConfig = new Document("_id", REPLICA_SET_NAME);
                replSetConfig.put("members", members);

                mongoClient = MongoClients.create(new ConnectionString("mongodb://localhost:" + embeddedPort));
                final MongoDatabase adminDatabase = mongoClient.getDatabase("admin");
                Mono.from(adminDatabase.runCommand(new Document("replSetInitiate", replSetConfig))).block();
            } finally {
                if (mongoClient != null) {
                    mongoClient.close();
                }
            }
        }
    }

    /**
     * Additional configuration to ensure that the replica set initialization happens after the
     * {@link MongodExecutable} bean is created. That's it - after the database is started.
     */
    @ConditionalOnClass({ MongoClient.class, MongodStarter.class })
    protected static class DependenciesConfiguration extends AbstractDependsOnBeanFactoryPostProcessor {

        DependenciesConfiguration() {
            super(EmbeddedMongoReplicaSetInitialization.class, null, MongodExecutable.class);
        }
    }
}
