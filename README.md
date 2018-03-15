# Reactive Streams Utilities

This is an exploration of what a utilities library for Reactive Streams in the JDK might look like.

## Glossary:

A short glossary for the sake of avoiding confusion and establish a shared vocabulary, for purpose of this proposal we define:

- Reactive Streams - for purpose of this proposal understood as the *execution semantics* defined by the http://www.reactive-streams.org specification.
- `juc.Flow` - the interfaces nested in the `java.util.concurrent.Flow` class, that is, `Publisher`, `Subscriber`, `Processor` and `Subscription`. These are a one to one mapping of the interfaces provided by the Reactive Streams specification.

## Goals:

* To fill a gap in the JDK where if a developer wants to do even the simplest of things with a JDK9 `j.u.c.Flow`, such as `map` or `filter`, they need to bring in a third party library that implements that.
  * :bulb: *Rationale:* This matters to JDK end-users as well as implementers of various JDK components (such as WebSocket, HTTP Client, ADBC and other APIs that *may* want to consider exposing reactive streams interfaces)
* To produce an API that can build `Publishers`, `Subscribers`, `Processors`, and complete graphs.
  * :bulb: *Rationale:* With the goal of being useful for building or consuming APIs that use reactive streams (for example, JDK9 Http Client and future APIs making use of reactive streams).
* To produce an API that aligns closely with `j.u.stream.Stream`, using it for inspiration for naming, scope, general API shape, and other aspects, however providing the alternative execution semantics as defined by `j.u.c.Flow`. 
  * :bulb: *Rationale:* Ensure familiarity of Java developers with the new API, as well as fit the JDK's established style of operators, limiting the number of concepts Java developers need to understand to do the different types of streaming offered by the JDK.
* To produce a Service Provider Interface (SPI) that can be implemented by multiple providers (including a Reference Implementation in the JDK itself), using the ServiceLoader mechanism to provide and load a default implementation (while allowing custom implementations to be manually provided). 
  * :bulb: *Rationale:* There are a lot of concerns that each different streams implementation provides and implements beyond streaming, for example monitoring/tracing, concurrency modelling, buffering strategies, performance aspects of the streams handling including fusing, and context (e.g. thread local) propagation. This will allow libraries to use and provide contracts based on this API without depending on a particular implementation, and allows developers to select the implementation that meets their needs.

## Non goals:

* To produce a "*rich*" set of operations for working with Reactive Streams (a.k.a. "kitchen sink"), the here defined operations should be the minimal useful set of operations. 
  * :bulb: *Rationale:* There already exist a number of Reactive Streams implementations that seek to meet this goal (eg, Akka Streams, Reactor, RxJava), and once you go past the basics (map, filter, collect), and start dealing with things like fan in/out, cycles, restarting, etc, the different approaches to solving this start to vary greatly. The JDK should provide enough to be useful for typical every day streaming use cases, with developers being able to select a third party library for anything more advanced.

## Approach

This proposal proposes an API based on the builder pattern, where operators such as `map` and `filter` are applied to builders, and the final result (eg, a `Publisher`, `Subscriber`, `Processor` or complete graph) is built by invoking a `build()` method.

Such an API allows for flexible implementation - it means that not each stage of the graph needs to implement Reactive Streams itself, instead, stages can be fused together in a straight forward way, and other aspects like context, monitoring and tracing that require out of band (from what Reactive Streams provides) transfer of state and signals can be implemented by the engine that builds the streams from the graph.

So if we take a use case - let's say we are consuming the Twitter streaming API, which emits a stream of new line separated JSON structures. To consume this stream, the JDK9 HTTP client requires application developers to supply a `Susbscriber<ByteBuffer>` to consume response bodies, we can build this subscriber like so:

```java
Subscriber<ByteBuffer> subscriber = 
  ReactiveStreams.<ByteBuffer>builder()
    // Assume parseLines is a utility that converts a
    // stream of arbitrarily chunked ByteBuffers to
    // ByteBuffers that represent one line
    .flatMapIterable(parseLines)
    // Assume parseJson is a function that takes
    // a ByteBuffer and returns a parsed JSON object
    .map(parseJson)
    // Asume saveToDatabase is a function that saves
    // the object to a database and returns a
    // CompletionStage of the result of the operation
    .flatMapCompletionStage(saveToDatabase)
    // And run by ignoring each element, since we've
    // handled them above already.
    .forEach(e -> {})
    .build().getSubscriber();
```

We now have a `Subscriber<ByteBuffer>` that we can wrap in a JDK9 HTTP client `BodyProcessor`, and pass that to the `send()` method.

To elaborate on the above API a little.

* `ReactiveStreams.builder()` returns a `ProcessorBuilder`. At this stage we are building a graph that has both an inlet (ie is a `Subscriber`) and an outlet (ie is a `Publisher`). If we invoked `.build()` at this stage, we would build a `Processor<ByteBuffer, ByteBuffer>`.
* `flatMapIterable` does a 1:n mapping of elements in memory, returning the results in an `Iterable`. It returns a new `ProcessorBuilder`.
* `map` is implemented in the current proposal, and it returns a new `ProcessorBuilder` that outputs the new type that was mapped to.
* `flatMapCompletionStage` does a 1:1 mapping of elements to elements asynchronously provided by a `CompletionStage`.
* `forEach` handles each element, and in this case we've handled them all alreday in `flatMapCompletionStage`. An important thing to note here is that this method doesn't return a `ProcessorBuilder`, since we have now provided a sink to consume the elements, so the shape of the graph has changed to a `SubscriberBuilder`.
* The `build` method returns a `SubscriberWithResult`. Many subscribers have some form of result, for example, a `toList` subscriber will produce a result that is a list of all the elements received, or a `reduce` subscriber will produce a result that is the result of a reduction function being applied to all the elements. So, when we build a subscriber, we also want to be able to return the result that that subscriber produces. In this case, we actually aren't interested in the result - though we could be, the result would be a `CompletionStage<Void>` that is redeemed when the stream completes either normally or with an error. The `SubscriberWithResult` naming is probably not good. `Accumulator` might be a better name.

So, in all, we have four different types of builders:

* `PublisherBuilder` - for building a `Publisher`.
* `ProcessorBuilder` - for building a `Processor`.
* `SubscriberBuilder` - for building a `SubscriberWithResult`, which is product of `Subscriber` and `CompletionStage`.
* `CompletionBuilder` - for building/running complete graphs, it produces a `CompletionStage` that is redeemed when the stream completes.

Here's an example of using `CompletionBuilder`:

```java
CompletionStage<MyObject> result = 
  ReactiveStreams.fromPublisher(somePublisher)
    .collect(Collectors.toList())
    .build();
```

In this case, we have taken a `Publisher` provided by some other API, at that stage we have a `PublisherBuilder`, and then using the `collect` method, we collect it into a list, this returns a `CompletionBuilder`, which we then build to run the stream and give us the result. The type that `collect` accepts is a `java.util.stream.Collector`, so this API is compatible with all the synchronous collectors already supplied out of the box in the JDK.

## Implementation

Underneath, this API builds a graph of stages that describe the processing. When `build` is invoked, this graph is passed to a `ReactiveStreamsEngine` to build the `Publisher`, `Subscriber`, `Processor` or `CompletionStage` as necessary. During this phase, the underlying engine implementation can do any processing on the graph it wants - it can fuse stages together, it can wrap callbacks in context supplying wrappers, etc. The `build` method is overloaded, one takse an explicit `ReactiveStreamsEngine`, the other looks up the `ReactiveStreamsEngine` using the JDK `ServiceLoader` mechanism.

An engine based on Akka Streams is already implemented, as is an engine based on RxJava. No work has been done on a zero dependency RI for the JDK, though there have been a few experimental efforts that could be used as a starter for this.

## TCK

A TCK has been implemented - at this stage it is very incomplete, but what it does demonstrate is how the Reactive Streams TCK provided by http://www.reactive-streams.org can be utilised to validate that the Reactive Streams interfaces built by this API are conforming Reactive Streams implementations.

## Next steps

The following work needs to be done:

* Decide on a set of stages/operators/generators specifec to Reactive Streams that are needed, beyond what the JDK8 Streams API has provided. For example, asynchronous generator functions based on CompletionStage might be useful, and perhaps a stream split function, maybe a cancelled/ignore subscribers, perhaps batching operators as well.
* Implement a zero dependency reference implementation.
