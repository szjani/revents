---
layout: default
title: "Aggregates"
permalink: /aggregates/
---

{% include breadcrumbs %}

## Aggregates
{:.no_toc}

* Toc
{:toc}

### Command handlers

Command handlers are public methods/constructors on aggregates. There must be no return value and there are two parameters: the first is the command itself, the second is a `CommandContext` object.
These methods also need to be annotated with `@CommandHandler`. Aggregate classes must also have a parameterless constructor in order the framework be able to instantiate the aggregate class.
Aggregate classes need to be annotated with `@Aggregate` annotation if we are relying on annotation scanning.

All parameter and state validation must be executed in command handlers.

### Event handlers

Event handlers in aggregates are used in the replay process. In order to dispatch a command, the framework reads all the events for the targeted aggregate and replays them.
This way the aggregate instance reaches its current state and ready to handle the incoming command. Therefore event handlers must not fail.

Storing the aggregate ID or its revision in the aggregate itself is not required by the framework.

```java
@Aggregate
public class Order {

    private OrderState orderState;

    private Order() {
    }

    @CommandHandler
    public Order(CreateOrder createOrder, CommandContext commandContext) {
        commandContext
            .registerAggregateId(Order.class, orderId.asString())
            .apply(new OrderCreated(orderId));
    }

    @CommandHandler
    public void handle(ApproveOrder approveOrder, CommandContext commandContext) {
        Preconditions.checkState(orderState != OrderState.REJECTED);
        commandContext.apply(new OrderApproved(approveOrder.orderId()));
    }

    @EventHandler
    void on(OrderCreated orderCreated) {
        orderState = OrderState.CREATED;
    }
}
```

### Creating another aggregate instance during command handling

If creating a new aggregate instance is needed, we have two naive options:
1. An event handler handles the event emitted by the command handler and it sends a new command to the `CommandGateway`. The system can create a new aggregate during the processing of this command.
2. The consumer (mostly application, or controller layer) fires a new command once the first command is handled.

The first approach is good but may needs boilerplate code. The seconds approach is worse as the domain knowledge is leaked.

Revents allows to request a new command dispatch from a command handler.

```java
@CommandHandler
public void handle(CloseRegistration closeRegistration, CommandContext context) {
    checkSessionIsNotClosed();
    context
        .apply(new RegistrationClosed(closeRegistration.getSessionId()))
        .request(new CalculateFinalStatistics(closeRegistration.getSessionId()));
}
```

It is important that the dispatching process of the requested command will be started only once the current one is done. Technically it is very similar to the second approach above with the difference that the command is requested by and aggregate instead of the controller layer.
