# Reactive Streams Utilities

This is an exploration of what a utilities library for Reactive Streams in the JDK might look like.

## Glossary:

A short glossary for the sake of avoiding confusion and establish a shared vocabulary, for purpose of this proposal we define:

- `j.u.c.Flow` - short for `java.util.concurrent.Flow`, the reactive stream type shipping with JDK9. For all intents and purposes the various reactive streams types mentioned in this proposal and documents 
- reactive streams - for purpose of this proposal understood as the *execution semantics* defined by http://www.reactive-streams.org specification, since the types themselfes do not matter this proposal, as they are 1:1 equivalent to the `j.u.c.Flow` ones.
- ...

## Goals:

* To fill a gap in the JDK where if a developer wants to do even the simplest of things with a JDK9 `j.u.c.Flow`, such as `map` or `filter`, they need to bring in a third party library that implements that.
  * :bulb: *Rationale:* This matters to JDK end-users as well as imlpementers of various JDK components (such as websocket, httpclient, adbc and other APIs that *may* want to consider exposing reactive streams interfaces)
* To produce an API that can build `Publishers`, `Subscribers`, `Processors`, and complete graphs.
  * :bulb: *Rationale:* With the goal of being useful for building or consuming APIs that use reactive streams (for example, JDK9 Http Client and future APIs making use of reactive streams).
* To produce an API that aligns closely with `j.u.stream.Stream`, using it for inspiration for naming, scope, general API shape, and other aspects, however providing the alternative execution semantics as defined by `j.u.c.Flow`. 
  * :bulb: *Rationale:* Ensure familiarity of Java developers with the new API, as well as fit the JDK's established style of operators, limiting the number of concepts Java developers need to understand to do the different types of streaming offered by the JDK.
* To produce an Service Provider Interface (SPI) that can be implemented by multiple providers (including an Reference Implementation in the JDK itself), using the ServiceLoader mechanism to provide and load a default implementation (while allowing custom implementations to be manually provided). 
  * :bulb: *Rationale:* There are a lot of concerns that each different streams implementation provides and implements, beyond streaming, for example monitoring/tracing, concurrency modelling, buffering strategies, performance aspects of the streams handling including fusing, and context (e.g. thread local) propagation. This will allow libraries to use and provide contracts based on this API without depending on a particular implementation, and allows developers to select the implementation that meets their needs.

## Non goals:

* To produce a "*rich*" set of operations for working with reactive streams (a.k.a. "kitchen sink"), the here defined operations should be the minimal useful set of operations. 
  * :bulb: *Rationale:* There already exist a number of reactive streams implementations that seek to meet this goal (eg, Akka Streams, Reactor, RxJava), and once you go past the basics (map, filter, collect), and start dealing with things like fan in/out, cycles, restarting, etc, the different approaches to solving this start to vary greatly. The JDK should provide enough to be useful for typical every day streaming use cases, with developers being able to select a third party library for anything more advanced.
