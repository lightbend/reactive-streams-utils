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

package org.reactivestreams.utils.impl;

import org.reactivestreams.utils.SubscriberWithResult;
import org.reactivestreams.utils.spi.Graph;
import org.reactivestreams.utils.spi.Stage;
import org.reactivestreams.utils.spi.UnsupportedStageException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A built graph.
 * <p>
 * This class is the main class responsible for building and running graphs.
 * <p>
 * Each stage in the graph is provided with {@link StageInlet}'s and {@link StageOutlet}'s to any inlets and outlets it
 * may have. Stages that feed into each other will be joined by a {@link StageOutletInlet}. On
 * {@link java.util.concurrent.Flow.Publisher} and {@link java.util.concurrent.Flow.Subscriber} ends of the graph, as
 * well as for publisher and subscriber stages, the ports are {@link SubscriberInlet} and {@link PublisherOutlet}.
 * <p>
 * So in general, a graph is a series of stages, each separated by {@link StageOutletInlet}, and started/ended by
 * {@link SubscriberInlet} and {@link PublisherOutlet} when the ends are open.
 * <p>
 * The graph itself is an executor. This executor guarantees that all operations submitted to it are run serially, on
 * a backed thread pool. All signals into the graph must be submitted to this executor. The executor also handles
 * exceptions, any exceptions caught by the executor will result in the entire graph shutting down.
 */
class BuiltGraph implements Executor {

  private static final int DEFAULT_BUFFER_HIGH_WATERMARK = 8;
  private static final int DEFAULT_BUFFER_LOW_WATERMARK = 4;

  private final Executor mutex;
  private final Deque<UnrolledSignal> unrolledSignals = new ArrayDeque<>();
  private final Set<Port> ports = new LinkedHashSet<>();
  private final Set<GraphStage> stages = new LinkedHashSet<>();

  private BuiltGraph(Executor threadPool) {
    this.mutex = new MutexExecutor(threadPool);
  }

  private static Builder newBuilder(Executor mutex) {
    BuiltGraph logic = new BuiltGraph(mutex);
    return logic.new Builder();
  }

  /**
   * Build a pubisher graph.
   */
  static <T> Flow.Publisher<T> buildPublisher(Executor mutex, Graph graph) {
    return newBuilder(mutex).buildGraph(graph, Shape.PUBLISHER).publisher();
  }

  /**
   * Build a subscriber graph.
   */
  static <T, R> SubscriberWithResult<T, R> buildSubscriber(Executor mutex, Graph graph) {
    return newBuilder(mutex).buildGraph(graph, Shape.SUBSCRIBER).subscriber();
  }

  /**
   * Build a processor graph.
   */
  static <T, R> Flow.Processor<T, R> buildProcessor(Executor mutex, Graph graph) {
    return newBuilder(mutex).buildGraph(graph, Shape.PROCESSOR).processor();
  }

  /**
   * Build a closed graph.
   */
  static <T> CompletionStage<T> buildCompletion(Executor mutex, Graph graph) {
    return newBuilder(mutex).buildGraph(graph, Shape.CLOSED).completion();
  }

  /**
   * Build a sub stage inlet.
   */
  <T> SubStageInlet<T> buildSubInlet(Graph graph) {
    return new Builder().buildGraph(graph, Shape.INLET).inlet();
  }

  /**
   * Used to indicate the shape of the graph we're building.
   */
  private enum Shape {
    PUBLISHER, SUBSCRIBER, PROCESSOR, CLOSED, INLET
  }

  /**
   * A builder.
   * <p>
   * Builders are used both to build new graphs, as well as to add sub streams to an existing graph.
   */
  private class Builder {
    /**
     * The first subscriber of this graph. If this graph has a processor or subscriber shape, then by the time the
     * graph is ready to be built, this will be non null.
     */
    private Flow.Subscriber firstSubscriber;
    /**
     * Last publisher for this graph. If this graph has a processor or publisher shape, then by the time the graph is
     * ready to be built, this will be non null.
     */
    private Flow.Publisher lastPublisher;
    /**
     * The last inlet for the graph. Is this graph has an inlet shape, then by the time the graph is ready to be built,
     * this will be non null.
     */
    private StageInlet lastInlet;
    /**
     * The result for the graph. If this graph has a subscriber or closed shape, then by the time the graph is ready to
     * be built, this will be non null.
     */
    private CompletableFuture result;
    /**
     * The stages that have been added to the graph by this builder.
     */
    private List<GraphStage> builderStages = new ArrayList<>();
    /**
     * The ports that have been added to the graph by this builder.
     */
    private List<Port> builderPorts = new ArrayList<>();

    /**
     * Build the graph.
     */
    private Builder buildGraph(Graph graph, Shape shape) {

      // If we're building a subscriber or closed graph, instantiate the result.
      if (shape == Shape.SUBSCRIBER || shape == Shape.CLOSED) {
        result = new CompletableFuture();
      }

      Collection<Stage> graphStages = graph.getStages();
      // Special case - an empty graph. This should result in an identity processor.
      // To build this, we use a single map stage with the identity function.
      if (graphStages.isEmpty()) {
        graphStages = List.of(new Stage.Map(Function.identity()));
      }

      // In the loop below, we need to compare each pair of consecutive stages, to work out what sort of inlet/outlet
      // needs to be between them. Publisher, Subscriber and Processor stages get treated specially, since they need
      // to feed in to/out of not an inlet, but a subscriber/publisher. So, we're looking for the following patterns:
      // * A publisher or processor stage to a subscriber or processor stage - no inlet/outlet is needed, these can
      //   feed directly to each other, and we connect them using a connector stage.
      // * A publisher or processor stage to an inlet stage, these get connected using a SubscriberInlet
      // * An outlet stage to a subscriber or processor stage, these get connected using a PublisherOutlet
      // * An outlet stage to an inlet stage, these get connected using a StageOutletInlet
      // Finally we need to consider the ends of the graph - if the first stage has no inlet, then no port is needed
      // there. Otherwise, we need a SubscriberInlet. And if the last stage has no outlet, then no port is needed there,
      // otherwise, we need a PublisherOutlet.
      //
      // As we iterate through the graph, we need to know what the previous stage is to be able to work out which port
      // to instantiate, and we need to keep a reference to either the previous inlet or publisher, so that we can
      // pass it to the next stage that we construct.
      Stage previousStage = null;
      StageInlet previousInlet = null;
      Flow.Publisher previousPublisher = null;

      for (Stage stage : graphStages) {

        StageOutlet currentOutlet = null;
        StageInlet currentInlet = null;
        Flow.Publisher currentPublisher = null;
        Flow.Subscriber currentSubscriber = null;

        // If this is the first stage in the graph
        if (previousStage == null) {
          if (isSubscriber(stage)) {
            // It's a subscriber, we don't create an inlet, instead we use it directly as the first subscriber
            // of this graph.
            if (stage instanceof Stage.Subscriber) {
              firstSubscriber = ((Stage.Subscriber) stage).getSubscriber();
            } else if (stage instanceof Stage.Processor) {
              firstSubscriber = ((Stage.Processor) stage).getProcessor();
            }
          } else if (stage.hasInlet()) {
            // Otherwise if it has an inlet, we need to create a subscriber inlet as the first subscriber.
            SubscriberInlet inlet = addPort(createSubscriberInlet());
            currentInlet = inlet;
            firstSubscriber = inlet;
          }
        } else {
          if (isPublisher(previousStage)) {
            if (isSubscriber(stage)) {
              // We're connecting a publisher to a subscriber, don't create any port, just record what the current
              // publisher is.
              if (previousStage instanceof Stage.Publisher) {
                currentPublisher = ((Stage.Publisher) previousStage).getPublisher();
              } else {
                currentPublisher = ((Stage.Processor) previousStage).getProcessor();
              }
            } else {
              // We're connecting a publisher to an inlet, create a subscriber inlet for that.
              SubscriberInlet inlet = addPort(createSubscriberInlet());
              currentInlet = inlet;
              currentSubscriber = inlet;
            }
          } else {
            if (isSubscriber(stage)) {
              // We're connecting an outlet to a subscriber, create a publisher outlet for that.
              PublisherOutlet outlet = addPort(new PublisherOutlet(BuiltGraph.this));
              currentOutlet = outlet;
              currentPublisher = outlet;
            } else {
              // We're connecting an outlet to an inlet
              StageOutletInlet outletInlet = addPort(new StageOutletInlet(BuiltGraph.this));
              currentOutlet = outletInlet.new Outlet();
              currentInlet = outletInlet.new Inlet();
            }
          }

          // Now that we know the inlet/outlet/subscriber/publisher for the previous stage, we can instantiate it
          addStage(previousStage, previousInlet, previousPublisher, currentOutlet, currentSubscriber);
        }

        previousStage = stage;
        previousInlet = currentInlet;
        previousPublisher = currentPublisher;
      }

      // Now we need to handle the last stage
      if (previousStage != null) {
        if (isPublisher(previousStage)) {
          if (shape == Shape.INLET) {
            // Last stage is a publisher, and we need to produce a sub stream inlet
            SubscriberInlet subscriberInlet = addPort(createSubscriberInlet());
            lastInlet = subscriberInlet;
            addStage(previousStage, null, null, null, subscriberInlet);
          } else {
            // Last stage is a publisher, and we need a publisher, no need to handle it, we just set it to be
            // the last publisher.
            if (previousStage instanceof Stage.Publisher) {
              lastPublisher = ((Stage.Publisher) previousStage).getPublisher();
            } else {
              lastPublisher = ((Stage.Processor) previousStage).getProcessor();
            }
          }
        } else if (previousStage.hasOutlet()) {
          StageOutlet outlet;
          if (shape == Shape.INLET) {
            // We need to produce an inlet, and the last stage has an outlet, so create an outlet inlet for that
            StageOutletInlet outletInlet = addPort(new StageOutletInlet(BuiltGraph.this));
            lastInlet = outletInlet.new Inlet();
            outlet = outletInlet.new Outlet();
          } else {
            // Otherwise we must be producing a publisher, to create a publisher outlet for that.
            PublisherOutlet publisherOutlet = addPort(new PublisherOutlet(BuiltGraph.this));
            outlet = publisherOutlet;
            lastPublisher = publisherOutlet;
          }
          // And add the stage
          addStage(previousStage, previousInlet, previousPublisher, outlet, null);
        } else {
          // There's no outlet or publisher, just wire it to the previous stage
          addStage(previousStage, previousInlet, previousPublisher, null, null);
        }
      }

      ports.addAll(builderPorts);
      stages.addAll(builderStages);

      return this;
    }

    /**
     * Verify that the ports in this builder are ready to start receiving signals - that is, that they all have their
     * listeners set.
     */
    private void verifyReady() {
      // Verify that the ports have listeners etc
      for (Port port : builderPorts) {
        port.verifyReady();
      }
    }

    private <T> SubStageInlet<T> inlet() {
      Objects.requireNonNull(lastInlet, "Not an inlet graph");
      assert result == null;
      assert firstSubscriber == null;
      assert lastPublisher == null;

      return new SubStageInlet(lastInlet, builderStages, builderPorts);
    }

    Flow.Publisher publisher() {
      Objects.requireNonNull(lastPublisher, "Not a publisher graph");
      assert result == null;
      assert firstSubscriber == null;
      assert lastInlet == null;

      verifyReady();
      startGraph();

      return lastPublisher;
    }

    SubscriberWithResult subscriber() {
      Objects.requireNonNull(firstSubscriber, "Not a subscriber graph");
      Objects.requireNonNull(result, "Not a subscriber graph");
      assert lastPublisher == null;
      assert lastInlet == null;

      verifyReady();
      startGraph();

      return new SubscriberWithResult(firstSubscriber, result);
    }

    CompletionStage completion() {
      Objects.requireNonNull(result, "Not a completion graph");
      assert lastPublisher == null;
      assert firstSubscriber == null;
      assert lastInlet == null;

      verifyReady();
      startGraph();

      return result;
    }

    Flow.Processor processor() {
      Objects.requireNonNull(lastPublisher, "Not a processor graph");
      Objects.requireNonNull(firstSubscriber, "Not a processor graph");
      assert result == null;

      verifyReady();
      startGraph();

      return new WrappedProcessor(firstSubscriber, lastPublisher);
    }

    /**
     * Add a stage.
     * <p>
     * The stage will be inspected to see what type of stage it is, and it will be created using the passed in inlet,
     * publisher, outlet or subscriber, according to what it needs.
     * <p>
     * It is up to the caller of this method to ensure that the right combination of inlet/publisher/outlet/subscriber
     * are not null for the stage it's creating.
     */
    private void addStage(Stage stage, StageInlet inlet, Flow.Publisher publisher, StageOutlet outlet,
        Flow.Subscriber subscriber) {

      // Inlets
      if (!stage.hasInlet()) {
        if (stage instanceof Stage.Of) {
          addStage(new OfStage(BuiltGraph.this, outlet,
              ((Stage.Of) stage).getElements()));
        } else if (stage instanceof Stage.Concat) {

          // Use this builder to build each of the sub stages that are being concatenated as an inlet graph, and then
          // capture the last inlet of each to pass to the concat stage.
          buildGraph(((Stage.Concat) stage).getFirst(), Shape.INLET);
          StageInlet firstInlet = lastInlet;
          lastInlet = null;

          buildGraph(((Stage.Concat) stage).getSecond(), Shape.INLET);
          StageInlet secondInlet = lastInlet;
          lastInlet = null;

          addStage(new ConcatStage(BuiltGraph.this, firstInlet, secondInlet, outlet));
        } else if (stage instanceof Stage.Publisher) {
          addStage(new ConnectorStage<>(BuiltGraph.this, ((Stage.Publisher) stage).getPublisher(), subscriber));
        } else if (stage instanceof Stage.Failed) {
          addStage(new FailedStage(BuiltGraph.this, outlet, ((Stage.Failed) stage).getError()));
        } else {
          throw new UnsupportedStageException(stage);
        }

        // Inlet/Outlets
      } else if (stage.hasOutlet()) {
        if (stage instanceof Stage.Map) {
          addStage(new MapStage(BuiltGraph.this, inlet, outlet,
              ((Stage.Map) stage).getMapper()));
        } else if (stage instanceof Stage.Filter) {
          addStage(new FilterStage(BuiltGraph.this, inlet, outlet, ((Stage.Filter) stage).getPredicate().get()));
        } else if (stage instanceof Stage.TakeWhile) {
          Predicate predicate = ((Stage.TakeWhile) stage).getPredicate().get();
          boolean inclusive = ((Stage.TakeWhile) stage).isInclusive();
          addStage(new TakeWhileStage(BuiltGraph.this, inlet, outlet, predicate, inclusive));
        } else if (stage instanceof Stage.FlatMap) {
          addStage(new FlatMapStage(BuiltGraph.this, inlet, outlet, ((Stage.FlatMap) stage).getMapper()));
        } else if (stage instanceof Stage.FlatMapCompletionStage) {
          addStage(new FlatMapCompletionStage(BuiltGraph.this, inlet, outlet, ((Stage.FlatMapCompletionStage) stage).getMapper()));
        } else if (stage instanceof Stage.FlatMapIterable) {
          addStage(new FlatMapIterableStage(BuiltGraph.this, inlet, outlet, ((Stage.FlatMapIterable) stage).getMapper()));
        } else if (stage instanceof Stage.Processor) {
          Flow.Processor processor = ((Stage.Processor) stage).getProcessor();
          addStage(new ConnectorStage(BuiltGraph.this, publisher, processor));
          addStage(new ConnectorStage(BuiltGraph.this, processor, subscriber));
        } else {
          throw new UnsupportedStageException(stage);
        }

        // Outlets
      } else {
        if (stage instanceof Stage.Collect) {
          addStage(new CollectStage(BuiltGraph.this, inlet, result, ((Stage.Collect) stage).getCollector()));
        } else if (stage instanceof Stage.FindFirst) {
          addStage(new FindFirstStage(BuiltGraph.this, inlet, result));
        } else if (stage instanceof Stage.Cancel) {
          addStage(new CancelStage(BuiltGraph.this, inlet, result));
        } else if (stage instanceof Stage.Subscriber) {
          // We need to capture termination, to do that we insert a CaptureTerminationStage between this and the
          // previous stage.
          if (inlet == null) {
            SubscriberInlet subscriberInlet = addPort(createSubscriberInlet());
            if (publisher != null) {
              addStage(new ConnectorStage(BuiltGraph.this, publisher, subscriberInlet));
            } else {
              firstSubscriber = subscriberInlet;
            }
            inlet = subscriberInlet;
          }
          PublisherOutlet publisherOutlet = addPort(new PublisherOutlet(BuiltGraph.this));
          addStage(new CaptureTerminationStage(BuiltGraph.this, inlet, publisherOutlet, result));
          addStage(new ConnectorStage(BuiltGraph.this, publisherOutlet, ((Stage.Subscriber) stage).getSubscriber()));
        } else {
          throw new UnsupportedStageException(stage);
        }
      }
    }

    private SubscriberInlet createSubscriberInlet() {
      return new SubscriberInlet(BuiltGraph.this, DEFAULT_BUFFER_HIGH_WATERMARK, DEFAULT_BUFFER_LOW_WATERMARK);
    }

    private <T extends Port> T addPort(T port) {
      builderPorts.add(port);
      return port;
    }

    private void addStage(GraphStage stage) {
      builderStages.add(stage);
    }

    private boolean isSubscriber(Stage stage) {
      return stage instanceof Stage.Subscriber || stage instanceof Stage.Processor;
    }

    private boolean isPublisher(Stage stage) {
      return stage instanceof Stage.Publisher || stage instanceof Stage.Processor;
    }

  }

  /**
   * Execute a signal on this graphs execution context.
   * <p>
   * This is the entry point for all external signals into the graph. The passed in command will be run with exclusion
   * from all other signals on this graph. Any exceptions thrown by the command will cause the graph to be terminated
   * with a failure.
   * <p>
   * Commands are also allowed to (synchronously) emit unrolled signals, by adding them to the unrolledSignals queue.
   * Unrolled signals are used for breaking infinite recursion scenarios. This method will drain all unrolled signals
   * (including subsequent signals emitted by the unrolled signals themselves) after invocation of the command.
   *
   * @param command The command to execute in this graphs execution context.
   */
  @Override
  public void execute(Runnable command) {
    mutex.execute(() -> {
      try {
        // First execute the runnable
        command.run();

        // Now drain a maximum of 32 signals from the queue
        int signalsDrained = 0;
        while (!unrolledSignals.isEmpty() && signalsDrained < 32) {
          signalsDrained++;
          unrolledSignals.poll().signal();
        }

        // If there were more than 32 unrolled signals, we resubmit
        // to the executor to allow us to receive external signals
        if (!unrolledSignals.isEmpty()) {
          execute(() -> {
          });
        }

      } catch (RuntimeException e) {
        // shut down the stream
        streamFailure(e);
        // Clear remaining signals
        unrolledSignals.clear();
      }
    });
  }

  /**
   * Start the whole graph.
   */
  private void startGraph() {
    execute(() -> {
      for (GraphStage stage : stages) {
        stage.postStart();
      }
    });
  }

  private void streamFailure(Throwable error) {
    // todo handle better
    error.printStackTrace();
    for (Port port : ports) {
      try {
        port.onStreamFailure(error);
      } catch (RuntimeException e) {
        // Ignore
      }
    }
    ports.clear();
  }

  /**
   * Enqueue a signal to be executed serially after the current signal processing finishes.
   */
  void enqueueSignal(UnrolledSignal signal) {
    unrolledSignals.add(signal);
  }

  /**
   * An inlet for connecting a sub stage.
   * <p>
   * This stage captures close signals, and removes the stages and ports from the graph so as to avoid leaking memory.
   */
  final class SubStageInlet<T> implements StageInlet<T> {
    private final StageInlet<T> delegate;
    private final List<GraphStage> subStages;
    private final List<Port> subStagePorts;

    private boolean started = false;

    private SubStageInlet(StageInlet<T> delegate, List<GraphStage> subStages, List<Port> subStagePorts) {
      this.delegate = delegate;
      this.subStages = subStages;
      this.subStagePorts = subStagePorts;
    }

    void start() {
      subStagePorts.forEach(Port::verifyReady);
      started = true;
      subStages.forEach(GraphStage::postStart);
    }

    private void shutdown() {
      stages.removeAll(subStages);
      ports.removeAll(subStagePorts);
    }

    @Override
    public void pull() {
      if (!started) {
        throw new IllegalStateException("Pull before the sub stream has been started.");
      }
      delegate.pull();
    }

    @Override
    public boolean isPulled() {
      return delegate.isPulled();
    }

    @Override
    public boolean isAvailable() {
      return delegate.isAvailable();
    }

    @Override
    public boolean isClosed() {
      return delegate.isClosed();
    }

    @Override
    public void cancel() {
      if (!started) {
        throw new IllegalStateException("Cancel before the sub stream has been started.");
      }
      delegate.cancel();
    }

    @Override
    public T grab() {
      if (!started) {
        throw new IllegalStateException("Grab before the sub stream has been started.");
      }
      return delegate.grab();
    }

    @Override
    public void setListener(InletListener listener) {
      delegate.setListener(new InletListener() {
        @Override
        public void onPush() {
          listener.onPush();
        }

        @Override
        public void onUpstreamFinish() {
          listener.onUpstreamFinish();
          shutdown();
        }

        @Override
        public void onUpstreamFailure(Throwable error) {
          listener.onUpstreamFailure(error);
          shutdown();
        }
      });
    }
  }

}


/**
 * An unrolled signal.
 * <p>
 * It is possible for stages to get into an infinite recursion, doing push/pulls between each other. This interface
 * allows them to unroll the recursion, by adding the signal to the unrolledSignals queue in this class, which then
 * gets executed after the first callback is executed.
 */
interface UnrolledSignal {
  void signal();
}

/**
 * A port, which may sit between two stages of this graph.
 */
interface Port {
  /**
   * If an exception is thrown by the graph, or otherwise encountered, each port will be shut down in the order they
   * were created, by invoking this. This method should implement any clean up associated with a port, if the port
   * isn't already shut down.
   */
  void onStreamFailure(Throwable reason);

  /**
   * Verify that this port is ready to start receiving signals.
   */
  void verifyReady();
}

