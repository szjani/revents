# Revents

![Java CI with Gradle](https://github.com/szjani/revents/workflows/Java%20CI%20with%20Gradle/badge.svg)

Revents is an event sourcing framework built on [Project Reactor](https://projectreactor.io/). The codebase is under
heavy development, not production ready yet!

Github pages documentation: https://szjani.github.io/revents/

## Build

Revents requires Java 11.

`./gradlew build`

## Features

- Java configuration via builders
- Spring Boot integration
- Annotation driven
- Reactive
- Supports [Immutables](https://immutables.github.io/)
- Snapshotting (time and event counter based)
- Revision check, optimistic locking
- Event store as queue, event acknowledge (persistent token)  
- R2dbc event store and snapshot repository implementation
- MongoDB token store implementation
- Fix and dynamic streams of events
- Customizable and extendable command/event processing flow
- Jackson serialization
- Slf4j MDC population from Reactor context
- In-memory implementations for testing purposes
- `AggregateTestHelper` for behavior testing of aggregates
- Time manipulation during tests

## Example code

```java
@Aggregate
public class Order {

    private OrderId orderId;
    private OrderState orderState;

    @CommandHandler
    public Order(CreateOrder createOrder, CommandContext commandContext) {
        OrderId orderId = OrderId.generate();
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
        orderId = orderCreated.orderId();
        orderState = OrderState.CREATED;
    }
}
```

```java
@Value.Immutable
public interface ApproveOrder {

    @TargetAggregate
    OrderId orderId();

    @Min(1) //javax.validation
    @ExpectedRevision
    Long version();
}
```

```java
@EventHandlerBean(processorId = "read-db-sync")
public class OrderProjection {

    @EventHandler
    public Mono<Void> handle(OrderCreated orderCreated, EventContext eventContext) {
        return readDb.insert(new OrderProjection(orderCreated.orderId()));
    }
}
```

```java
CommandGateway commandGateway = buildCommandGateway();

commandGateway.sendWithResult(new CreateOrder())
    .map(CommandResult::aggregateId)
    .map(AggregateId::aggregateRootId)
    .flatMap(id -> commandGateway.send(ImmutableApproveOrder.builder()
        .orderId(OrderId.of(id))
        .version(1L)
        .build()))
    .subscribe();
```

## Creating custom implementations

`revents-contracts` helps you to create your custom implementation of the followings:
 - `EventStore`
 - `TokenStore`
 - `SnapshotRepository`

Extend the corresponding contract test class and ensure your implementation satisfies all the requirements.