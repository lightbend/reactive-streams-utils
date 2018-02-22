# Reactive Streams Utilities

This is an exploration of what a utilities library for Reactive Streams in the JDK might look like.

The following goals for this effort have been set:

* To fill a gap in the JDK where if a developer wants to do even the simplest of things with a JDK9 juc.Flow, such as map or filter, they need to bring in a third party library that implements that.
* To produce an API that can build Publishers, Subscribers, Processors, and complete graphs, for the purposes of consuming APIs that use reactive streams (for example, JDK9 Http Client).
* To produce an API that aligns closely with ju.stream.Stream, using it for inspiration for naming, scope, general API shape, and other aspects. The purpose of this goal is to ensure familiarity of Java developers with the new API, and to limit the number of concepts Java developers need to understand to do the different types of streaming offered by the JDK.
* To produce an API that can be implemented by multiple providers (including an RI in the JDK itself), using the ServiceLoader mechanism to provide and load a default implementation (while allowing custom implementations to be manually provided). There are a lot of concerns that each different streams implementation provides and implements, beyond streaming, for example monitoring/tracing, concurrency modelling, buffering strategies, performance aspects of the streams handling including fusing, and context (eg thread local) propagation. This will allow libraries to use and provide contracts based on this API without depending on a particular implementation, and allows developers to select the implementation that meets their needs.

Non goals:

* To produce a kitchen sink of utilities for working with reactive streams. There already exist a number of reactive streams implementations that seek to meet this goal (eg, Akka Streams, Reactor, RxJava), and once you go past the basics (map, filter, collect), and start dealing with things like fan in/out, cycles, restarting, etc, the different approaches to solving this start to vary greatly. The JDK should provide enough to be useful for typical every day streaming use cases, with developers being able to select a third party library for anything more advanced.