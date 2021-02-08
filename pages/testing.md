---
layout: default
title: "Testing"
permalink: /testing/
---

{% include breadcrumbs %}

## Testing
{:.no_toc}

* Toc
{:toc}

Testing the command dispatch process and the business logic inside our aggregates can be easily done with `AggregateTestHelper`.

It follows the Given/When/Then approach:

 - Given: initializing the aggregate with replaying some events, simulating the current state
 - When: firing a command
 - Then: expecting 1 or more events emitted by the system

```java
OrderId orderId = OrderId.create();
AggregateTestHelper.testing(Order.class)
    .given(new OrderCreated(orderId))
    .when(new ApproveOrder(orderId))
    .thenExpectSingle(OrderApproved.class, event -> assertThat(event.orderId())
        .isEqualTo(orderId))
```

### Given

The `.given()` method can be called without argument, which means we are testing the creation of a new aggregate.
Multiple arguments can be passed, the order of the events is important as it is kept during the aggregate initialization process.
Calling further `.andGiven(event)` method is also possible.

#### Manipulating the time

Revents uses `ReventsClock.system()` as a system clock, which is `Clock.system(ZoneId.of("UTC"))` by default. Overriding this clock
on system level can be done via `ReventsClock.overrideSystemClock(newClock)`. However if we only want to manipulate the time during
testing then the related methods in `AggregateTestHelper` sould rather be used. We can override the `Clock`, set an `Instant` or 
a `ZonedDateTime` to simulate the current date/time (a.k.a. now).

### Then

We also have several options to validate the aggregate's behavior. Returning all the emitted events, or executing assertions
against 1, or all events is achievable with `then*()` methods. Expected errors can also be validated. Using AssertJ is not mandatory,
but recommended.
