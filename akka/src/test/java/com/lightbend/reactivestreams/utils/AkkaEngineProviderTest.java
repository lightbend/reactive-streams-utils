package com.lightbend.reactivestreams.utils;

import akka.Done;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.JavaFlowSupport;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import org.reactivestreams.utils.ReactiveStreams;
import org.testng.annotations.Test;
import scala.compat.java8.FutureConverters;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class AkkaEngineProviderTest {

  @Test
  public void akkaEngineProviderIsProvided() throws Exception {
    assertEquals(
        ReactiveStreams.of(1).toList().build()
            .toCompletableFuture().get(1, TimeUnit.SECONDS),
        List.of(1));
  }

  @Test
  public void actorSystemIsCleanedUpWhenThereAreNoMoreReferences() throws Exception {
    // First get a reference
    AkkaEngine engine = AkkaEngineProvider.provider();
    // And get the actor system from it
    ActorSystem system = ((ActorMaterializer) engine.materializer).system();
    // Clear reference
    engine = null;
    // Wait a while in case there are streams running from other tests
    Thread.sleep(300);
    // And gc
    System.gc();
    // Now wait for the system to shutdown
    FutureConverters.toJava(system.whenTerminated()).toCompletableFuture().get(10, TimeUnit.SECONDS);
  }

  @Test
  public void aRunningStreamShouldPreventActorSystemFromShuttingDown() throws Exception {
    AkkaEngine engine = AkkaEngineProvider.provider();
    ActorSystem system = ((ActorMaterializer) engine.materializer).system();

    Flow.Publisher<Done> publisher =
        Source.tick(
            Duration.create(100, TimeUnit.MILLISECONDS),
            Duration.create(100, TimeUnit.MILLISECONDS),
            Done.getInstance()
        )
            .runWith(JavaFlowSupport.Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), engine.materializer);

    AtomicReference<RuntimeException> error = new AtomicReference<>();
    ReactiveStreams.fromPublisher(publisher).forEach(d -> {
      if (error.get() != null) {
        throw error.get();
      }
    }).build(engine);
    publisher = null;

    engine = null;
    Thread.sleep(300);

    // And gc
    System.gc();
    // Wait for it to possibly complete
    Thread.sleep(1000);
    // Now ensure it doesn't complete
    assertFalse(system.whenTerminated().isCompleted());

    // Stop the stream
    error.set(new RuntimeException());
    // Wait for the stream to shutdown
    Thread.sleep(1000);
    // gc again
    System.gc();
    // Now ensure it does complete
    FutureConverters.toJava(system.whenTerminated()).toCompletableFuture().get(10, TimeUnit.SECONDS);
  }

}
