---
layout: default
title: "Configuration"
permalink: /configuration/
---

{% include breadcrumbs %}

## Configuration
{:.no_toc}

* Toc
{:toc}

### Spring Boot

Revents provide Spring Boot autoconfiguration by its `revents-spring-boot-autoconfigure` module. In order to use R2dbc based `EventStore`
and `SnapshotRepository`, and MongoDB based `TokenStore`, put the following configuration into your `application.yml`:

```yaml
revents:
  scanPackage: com.my.package
  mongo:
    token-store:
      enabled: true
  r2dbc:
    snapshot-repository:
      enabled: true
    event-store:
      enabled: true
```

Revents will scan the given package for classes annotated with `@Aggregate` and `@SerializedType`.

All the beans provided by Revents can be overridden.

#### Bean overrides

##### Command message interceptors

`commandMessageInterceptors` bean is a list of `MessageInterceptor<CommandMessage<?>, CommandResult>` objects. If you would like to use
javax.validation to validate command messages and also want to store events created by aggregate then define it in a `@Configuration` class:

```java
@Bean("commandMessageInterceptors")
@ConditionalOnMissingBean(name = "commandMessageInterceptors")
List<MessageInterceptor<CommandMessage<?>, CommandResult>> commandMessageInterceptors(
    Validator validator,
    EventStore eventStore) {

    return List.of(
        new ValidationMessageInterceptor<>(validator),
        new EventStoringCommandInterceptor(eventStore));
}
```

##### Event message dispatch interceptors

You might also want to execute all MongoDB statements inside a transaction, including the token storing ones triggered by the event acknowledge.
This can be done with the following:

```java
@Bean("eventMessageDispatchInterceptors")
Map<EventProcessor.EventProcessorId, List<MessageInterceptor<EventMessage<?>, Void>>> eventMessageDispatchInterceptors(
    MongoClient client) {

    return Map.of(EventProcessor.EventProcessorId.DEFAULT, List.of(
        new MongoTransactionalMessageInterceptor<>(client),
        new EventMessageAcknowledgeInterceptor()));
}
```

Note, that this wraps the whole event dispatch process into 1 MongoDB transaction, regardless of the number of event handlers executed.

##### Initializing the SQL schema

```java
@Bean
ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
    ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
    initializer.setConnectionFactory(connectionFactory);
    CompositeDatabasePopulator populator = new CompositeDatabasePopulator();
    populator.addPopulators(new ResourceDatabasePopulator(new ClassPathResource("revents-db-schema.sql")));
    initializer.setDatabasePopulator(populator);
    return initializer;
}
```

### Java configuration

Using Spring Boot is not mandatory, configuring Revents is possible with Java configuration.

`EventSourcingIt.java` contains a simple, in-memory based setup:

Event store contains all the events:
```java
var eventStore = new InMemoryEventStore(new InMemoryTokenStore());
```

Aggregate loader can load existing aggregates from a persistent storage:
```java
var aggregateLoader = EventSourcedBasedAggregateLoader.create(config -> config.eventStore(eventStore));
```

Commands need to be dispatched to the appropriate command handlers located in aggregates:
```java
var commandMessageDispatcher = AnnotationAwareCommandMessageDispatcher.create(config -> config
    .aggregateLoader(aggregateLoader)
    .scanAggregateClassesIn("com.my.package"));
```

`CommandBus` delivers the `CommandMessage`:
```java
var commandBus = DefaultCommandBus.create(busConfig -> busConfig
    .addCommandMessageInterceptors(
        new LoggingMessageInterceptor<>(),
        new EventStoringCommandInterceptor(eventStore))
    .commandMessageHandler(commandMessageDispatcher));
```

`CommandGateway` wraps the `CommandBus`, allows clients to send command objects:
```java
var commandGateway = LocalCommandGateway.create(config -> config
    .commandAggregateIdResolver(commandMessageDispatcher)
    .commandBus(commandBus));
```

Event handler object - containing `@EventHandler` methods -  and event processor configuration:
```java
var eventHandler = new EventHandler();

var eventProcessor = TrackingEventProcessor.create(config -> config
    .eventStore(eventStore)
    .addEventMessageDispatchInterceptors(
        new MongoTransactionalMessageInterceptor<>(client),
        new EventMessageAcknowledgeInterceptor())
    .annotationBasedEventMessageHandler(messageHandlerConfig -> messageHandlerConfig
        .addEventMessageHandlerInterceptors((message, chain) -> Mono.just(1)
            .transform(monoLogOnSubscribe(x -> LOG.info("Event handler interceptor - before")))
            .flatMap(x -> chain.handle(message))
            .transform(monoLogOnSuccess(x -> LOG.info("Event handler interceptor - after"))))
        .addEventHandlers(eventHandler)));

eventProcessor.run();
```
