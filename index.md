---
layout: default
title: Revents manual
---

## Introduction

Welcome to the Revents manual!

Revents is framework built upon [Reactor](https://projectreactor.io/) to help creating CQRS and Event Sourcing applications.

Its main features:

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

## Manual

 - [Messaging concept](messaging-concept)
 - [Aggregates](aggregates)
 
