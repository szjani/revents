---
layout: default
title: "Messaging concept"
permalink: /messaging-concept/
---

## Messaging concept

1. The generated Toc will be an ordered list
{:toc}

Each Revents message must implement the `Message` interface, which encapsulates the actual payload and the meta information.
The meta information contains a unique message ID, the time when the message was created, and an optional aggregate revision field.

Application developers do not meet with the `Message` interface, however framework developers may use it usually when implementing custom `MessageInterceptor`s,
to extend either the command or event processing flow.

### Commands

Wrapping the command payload into a `CommandMessage` is done by the framework, the message itself is exposed to the application very rarely. Therefore `command` reflects
to the command payload in this documentation.

The command can be any type of object but it is recommended to enforce immutability. In order to let the framework know how specific information can be extracted from the command,
Revents expects a few annotations on certain methods in the commands.

#### Targeting an aggregate instance

If we would like a certain command object to be dispatched to an already existing aggregate then the aggregate ID must be available in the command. This method must be annotated
with `@TargetAggregate`. The returned object can be any instance but its `toString()` method is used by the framework to find the aggregate instance.

```java
@Value.Immutable
public interface ApproveOrder {

    @TargetAggregate
    OrderId orderId();
}
```

#### Aggregate version

Commands originated from the user interface may be created based on an outdated version of the aggregate. It is possible to specify what aggregate revision the command was built on,
thus the command processing may fail due to version mismatch. This method must return any `Number` instance, the method must be annotated with `@ExpectedRevision`.

```java
@Value.Immutable
public interface ApproveOrder {

    @TargetAggregate
    OrderId orderId();

    @ExpectedRevision
    Long version();
}
```

### Events

Just like commands, events are also wrapped. In this case `EventMessage` is used for this purpose. There is no restriction for the payload (event), using immutability is encouraged.

### Message interceptors

`MessageInterceptor`s can be chained, each interceptor is called when a message is being delivered. The concept is very similar to `WebFilter` in spring-web. It is possible to execute our logic before and/or after the upcoming interceptor is called.

Each command must be handled on by one command handler. On the other hand, events can be handled by multiple event handlers. This difference also has impact on the message delivery flow.

### Command dispatching

The followings happen whenever a command is sent to a `CommandGateway`:

1. The aggregate ID and the revision is extracted from the command
2. The command is wrapped into a `CommandMessage` object
3. The command is pushed through a new chain created from the configured `MessageInterceptor`s
4. The aggregate is instantiated and all the existing events are replayed on it
5. The command handler method is called with the command and a `CommandContext`
6. The aggregate may register new events into the context. If the new aggregate's ID is generated in the constructor, it needs to be also registered into the context.
7. Once the command handler is called, `EventStoringCommandInterceptor` get all the events from the command context and saves them into the `EventStore`

### Event dispatching

Instead of calling all the event handlers whenever an event has been persisted, a separated flow is responsible to consume and dispatch events.

`EventProcessor` instances are running as a background job and consuming the persisted events from the `EventStore`. As only persisted events are consumed, storing the event and
handling that within the same transaction is not possible. This ensures that event handlers do not have impact on the event storing flow.

This approach also ensures that the consuming flow can be tracked. Every time an event processor successfully consumed an event, the event can be acknowledged. Thus even if the 
processor stops due to an error or an application deployment, the processor can continue the work from the last acknowledged event. Every `EventStore` must support this either directly or with the help of a `TokenStore`.

The followings happen whenever an event processor reads an event stream and gets an event:

1. The processor pushes the event through an interceptor chain. This chain is called dispatch chain. We can completely drop an event for example.
2. Once the event reaches the end of the dispatch chain, it is identified what event handlers should be called with the event.
3. For each event handler method a new interceptor chain is created. These chains are called event handler chains. With this chain it is possible to ignore/drop an event on handler method level.
4. After the event is handled by all the handlers, the `EventMessageAcknowledgeInterceptor` in the dispatcher chain acknowledges the event.
