layout: page
title: "Messaging concept"
permalink: /messaging-concept/

## Messaging concept

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
