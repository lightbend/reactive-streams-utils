/******************************************************************************
 * Licensed under Public Domain (CC0)                                         *
 *                                                                            *
 * To the extent possible under law, the person who associated CC0 with       *
 * this code has waived all copyright and related or neighboring              *
 * rights to this code.                                                       *
 *                                                                            *
 * You should have received a copy of the CC0 legalcode along with this       *
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.     *
 ******************************************************************************/

package com.lightbend.reactivestreams.utils;

import akka.actor.ActorSystem;
import akka.actor.BootstrapSetup;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.ConfigFactory;
import org.reactivestreams.utils.ReactiveStreamsEngine;
import org.reactivestreams.utils.SubscriberWithResult;
import org.reactivestreams.utils.spi.Graph;
import org.reactivestreams.utils.spi.UnsupportedStageException;
import scala.compat.java8.FutureConverters;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;

/**
 * Provides the Akka Engine to the JDK9 modules system.
 * <p>
 * This will instantiate its own actor systems to run the streams. It uses weak references and a cleaner to ensure
 * that the actor system gets cleaned up.
 * <p>
 * While JDK9 modules provided using a module descriptor can provide static method, right now we're providing this
 * module as a classpath service, and that doesn't support static methods. So, when used as an engine, this class
 * itself is used as the engine, and delegates to the actual engine, which has a weak reference kept to it.
 */
public class AkkaEngineProvider implements ReactiveStreamsEngine {

  /**
   * Used to clean up the actor system when the engine is no longer strongly referenceable.
   */
  private static final Cleaner actorSystemCleaner = Cleaner.create(runnable -> {
    Thread thread = new Thread(runnable);
    // The default thread factory used by the cleaner sets the thread priority to this.
    thread.setPriority(Thread.MAX_PRIORITY - 2);
    thread.setName("Akka-Reactive-Streams-Engine-Cleaner");
    return thread;
  });

  private static volatile WeakReference<AkkaEngine> cachedEngine = null;

  /**
   * Used to ensure only one instance of the engine exists at a time.
   */
  private static final Object mutex = new Object();

  /**
   * Using a static class rather than a lambda as advised by the Cleaner javadocs to ensure that we don't accidentally
   * close over a reference to the AkkaEngine.
   */
  private static class ActorSystemCleanerTask implements Runnable {
    private final ActorSystem system;

    private ActorSystemCleanerTask(ActorSystem system) {
      this.system = system;
    }

    @Override
    public void run() {
      system.terminate();
      // Let it terminate asynchronously, blocking while it does won't achieve anything.
    }
  }

  private static AkkaEngine createEngine() {
    ActorSystem system = ActorSystem.create("reactive-streams-engine",
        BootstrapSetup.create()
            // Use JDK common thread pool rather than instantiate our own.
            .withDefaultExecutionContext(FutureConverters.fromExecutorService(ForkJoinPool.commonPool()))
            // Be explicit about the classloader.
            .withClassloader(AkkaEngine.class.getClassLoader())
            // Use empty config to ensure any other actor systems using the root config don't conflict.
            // todo maybe we want to be able to configure it?
            .withConfig(ConfigFactory.empty())
    );
    Materializer materializer = ActorMaterializer.create(system);

    AkkaEngine engine = new AkkaEngine(materializer);
    actorSystemCleaner.register(engine, new ActorSystemCleanerTask(system));
    return engine;
  }

  /**
   * This method is used by the JDK9 modules service loader mechanism to load the engine.
   */
  public static AkkaEngine provider() {

    AkkaEngine engine = null;
    // Double checked locking to initialize the weak reference the first time this method
    // is invoked.
    if (cachedEngine == null) {
      synchronized (mutex) {
        if (cachedEngine == null) {
          // We could just use the weak reference to get the engine later, but technically,
          // it could be collected before we get there, so we strongly reference it here to
          // ensure that doesn't happen.
          engine = createEngine();
          cachedEngine = new WeakReference<>(engine);
        }
      }
    }
    if (engine == null) {
      // Double checked locking to ensure the weak reference is set.
      engine = cachedEngine.get();
      if (engine == null) {
        synchronized (mutex) {
          engine = cachedEngine.get();
          if (engine == null) {
            engine = createEngine();
            cachedEngine = new WeakReference<>(engine);
          }
        }
      }
    }

    return engine;
  }

  private final AkkaEngine delegate;

  public AkkaEngineProvider() {
    this.delegate = provider();
  }

  @Override
  public <T> Flow.Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
    return delegate.buildPublisher(graph);
  }

  @Override
  public <T, R> SubscriberWithResult<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
    return delegate.buildSubscriber(graph);
  }

  @Override
  public <T, R> Flow.Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
    return delegate.buildProcessor(graph);
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
    return delegate.buildCompletion(graph);
  }
}
