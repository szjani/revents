---
layout: default
title: "Event handlers"
permalink: /event-handlers/
---

{% include breadcrumbs %}

## Event handlers
{:.no_toc}

* Toc
{:toc}

Event handler objects are singletons, registered into one (or even more) `EventProcessor`s. Event handler methods must satisfy
the following requirements:

1. Annotated with `@EventHandler`
2. The first parameter is the event payload, can be an instance of any type
3. The second parameter is an `EventContext`
4. The method must return `Mono<Void>`

In case of Spring Boot configuration, using `@EventHandlerBean` class level annotation is possible. An instance of the class will be a singleton
Spring bean. The event processor, attached the handler object to, can be defined with the annotation.

```java
@EventHandlerBean(processorId = "email-sender")
public class EmailSender {

    @EventHandler
    public Mono<Void> handle(UserRegistrationRequested event) {
        return mailService.sendRegistrationMail(event.getUserId(), event.getEmail(), event.getUsername());
    }
}
```

### Event processors

Event processors can be created programmatically, or implicitly by the `@EventHandlerBean` annotation. All event handler methods a specific
event processor is responsible for are executed parallel with the same event. See `AnnotationAwareEventMessageDispatcher`.
