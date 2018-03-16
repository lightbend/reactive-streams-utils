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

class GraphLogic implements Executor {

  private static final int DEFAULT_BUFFER_HIGH_WATERMARK = 8;
  private static final int DEFAULT_BUFFER_LOW_WATERMARK = 4;

  private final Executor mutex;
  private final Deque<UnrolledSignal> unrolledSignals = new ArrayDeque<>();
  private final Set<Port> ports = new LinkedHashSet<>();
  private final Set<GraphStage> stages = new LinkedHashSet<>();

  private GraphLogic(Executor mutex) {
    this.mutex = mutex;
  }

  private static Builder newBuilder(Executor mutex) {
    GraphLogic logic = new GraphLogic(mutex);
    return logic.new Builder();
  }

  static <T> Flow.Publisher<T> buildPublisher(Executor mutex, Graph graph) {
    return newBuilder(mutex).materializeGraph(graph, Shape.PUBLISHER).publisher();
  }

  static <T, R> SubscriberWithResult<T, R> buildSubscriber(Executor mutex, Graph graph) {
    return newBuilder(mutex).materializeGraph(graph, Shape.SUBSCRIBER).subscriber();
  }

  static <T, R> Flow.Processor<T, R> buildProcessor(Executor mutex, Graph graph) {
    return newBuilder(mutex).materializeGraph(graph, Shape.PROCESSOR).processor();
  }

  static <T> CompletionStage<T> buildCompletion(Executor mutex, Graph graph) {
    return newBuilder(mutex).materializeGraph(graph, Shape.CLOSED).completion();
  }

  <T> SubStageInlet<T> buildInlet(Graph graph) {
    return new Builder().materializeGraph(graph, Shape.INLET).buildInlet();
  }

  private enum Shape {
    PUBLISHER, SUBSCRIBER, PROCESSOR, CLOSED, INLET
  }

  class Builder {
    private Flow.Subscriber firstSubscriber;
    private Flow.Publisher lastPublisher;
    private StageInlet lastInlet;
    private CompletableFuture result;
    private List<GraphStage> builderStages = new ArrayList<>();
    private List<Port> builderPorts = new ArrayList<>();

    private Builder materializeGraph(Graph graph, Shape shape) {
      Stage previousStage = null;

      if (shape == Shape.SUBSCRIBER || shape == Shape.CLOSED) {
        result = new CompletableFuture();
      }

      StageInlet previousInlet = null;
      Flow.Publisher previousPublisher = null;

      if (graph.getStages().isEmpty()) {
        SubscriberInlet inlet = new SubscriberInlet(DEFAULT_BUFFER_HIGH_WATERMARK, DEFAULT_BUFFER_LOW_WATERMARK);
        PublisherOutlet outlet = new PublisherOutlet();
        builderPorts.add(inlet);
        builderPorts.add(outlet);
        firstSubscriber = inlet;
        lastPublisher = outlet;
        // use an identity map stage
        addStage(new MapStage(GraphLogic.this, inlet, outlet, Function.identity()));
      }

      for (Stage stage: graph.getStages()) {

        StageOutlet currentOutlet = null;
        StageInlet currentInlet = null;
        Flow.Publisher currentPublisher = null;
        Flow.Subscriber currentSubscriber = null;

        if (previousStage == null) {
          if (isSubscriber(stage)) {
            if (stage instanceof Stage.Subscriber) {
              firstSubscriber = ((Stage.Subscriber) stage).getSubscriber();
            } else if (stage instanceof Stage.Processor) {
              firstSubscriber = ((Stage.Processor) stage).getProcessor();
            }
          } else if (stage.hasInlet()) {
            SubscriberInlet inlet = new SubscriberInlet(DEFAULT_BUFFER_HIGH_WATERMARK, DEFAULT_BUFFER_LOW_WATERMARK);
            builderPorts.add(inlet);
            currentInlet = inlet;
            firstSubscriber = inlet;
          }
        } else {
          if (isPublisher(previousStage)) {
            if (isSubscriber(stage)) {
              if (previousStage instanceof Stage.Publisher) {
                currentPublisher = ((Stage.Publisher) previousStage).getPublisher();
              } else {
                currentPublisher = ((Stage.Processor) previousStage).getProcessor();
              }
            } else {
              SubscriberInlet inlet = new SubscriberInlet(DEFAULT_BUFFER_HIGH_WATERMARK, DEFAULT_BUFFER_LOW_WATERMARK);
              builderPorts.add(inlet);
              currentInlet = inlet;
              currentSubscriber = inlet;
            }
          } else {
            if (isSubscriber(stage)) {
              PublisherOutlet outlet = new PublisherOutlet();
              builderPorts.add(outlet);
              currentOutlet = outlet;
              currentPublisher = outlet;
            } else {
              StageOutletInlet outletInlet = new StageOutletInlet();
              builderPorts.add(outletInlet);
              currentOutlet = outletInlet.new Outlet();
              currentInlet = outletInlet.new Inlet();
            }
          }

          addStage(previousStage, previousInlet, previousPublisher, currentOutlet, currentSubscriber);
        }

        previousStage = stage;
        previousInlet = currentInlet;
        previousPublisher = currentPublisher;
      }

      // Previous stage is the last stage
      if (previousStage != null) {
        if (isPublisher(previousStage)) {
          if (shape == Shape.INLET) {
            SubscriberInlet subscriberInlet = new SubscriberInlet(DEFAULT_BUFFER_HIGH_WATERMARK, DEFAULT_BUFFER_LOW_WATERMARK);
            builderPorts.add(subscriberInlet);
            lastInlet = subscriberInlet;
            addStage(previousStage, null, null, null, subscriberInlet);
          } else {
            if (previousStage instanceof Stage.Publisher) {
              lastPublisher = ((Stage.Publisher) previousStage).getPublisher();
            } else {
              lastPublisher = ((Stage.Processor) previousStage).getProcessor();
            }
          }
        } else if (previousStage.hasOutlet()) {
          StageOutlet outlet;
          if (shape == Shape.INLET) {
            StageOutletInlet outletInlet = new StageOutletInlet();
            builderPorts.add(outletInlet);
            lastInlet = outletInlet.new Inlet();
            outlet = outletInlet.new Outlet();
          } else {
            PublisherOutlet publisherOutlet = new PublisherOutlet();
            builderPorts.add(publisherOutlet);
            outlet = publisherOutlet;
            lastPublisher = publisherOutlet;
          }
          addStage(previousStage, previousInlet, previousPublisher, outlet, null);
        } else {
          addStage(previousStage, previousInlet, previousPublisher, null, null);
        }
      }

      ports.addAll(builderPorts);
      stages.addAll(builderStages);

      return this;
    }

    private void verifyReady() {
      // Verify that the ports have listeners etc
      for (Port port: builderPorts) {
        port.verifyReady();
      }
    }

    private <T> SubStageInlet<T> buildInlet() {
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

    private void addStage(Stage stage, StageInlet inlet, Flow.Publisher publisher, StageOutlet outlet,
        Flow.Subscriber subscriber) {

      // Inlets
      if (!stage.hasInlet()) {
        if (stage instanceof Stage.Of) {
          addStage(new OfStage(GraphLogic.this, outlet,
              ((Stage.Of) stage).getElements()));
        } else if (stage instanceof Stage.Concat) {

          materializeGraph(((Stage.Concat) stage).getFirst(), Shape.INLET);
          StageInlet firstInlet = lastInlet;
          lastInlet = null;

          materializeGraph(((Stage.Concat) stage).getSecond(), Shape.INLET);
          StageInlet secondInlet = lastInlet;
          lastInlet = null;

          addStage(new ConcatStage(GraphLogic.this, firstInlet, secondInlet, outlet));
        } else if (stage instanceof Stage.Publisher) {
          addStage(new ConnectorStage<>(GraphLogic.this, ((Stage.Publisher) stage).getPublisher(), subscriber));
        } else if (stage instanceof Stage.Failed) {
          addStage(new FailedStage(GraphLogic.this, outlet, ((Stage.Failed) stage).getError()));
        } else {
          throw new UnsupportedStageException(stage);
        }

      // Inlet/Outlets
      } else if (stage.hasOutlet()) {
        if (stage instanceof Stage.Map) {
          addStage(new MapStage(GraphLogic.this, inlet, outlet,
              ((Stage.Map) stage).getMapper()));
        } else if (stage instanceof Stage.Filter) {
          addStage(new FilterStage(GraphLogic.this, inlet, outlet, ((Stage.Filter) stage).getPredicate().get()));
        } else if (stage instanceof Stage.TakeWhile) {
          Predicate predicate = ((Stage.TakeWhile) stage).getPredicate().get();
          boolean inclusive = ((Stage.TakeWhile) stage).isInclusive();
          addStage(new TakeWhileStage(GraphLogic.this, inlet, outlet, predicate, inclusive));
        } else if (stage instanceof Stage.FlatMap) {
          addStage(new FlatMapStage(GraphLogic.this, inlet, outlet, ((Stage.FlatMap) stage).getMapper()));
        } else if (stage instanceof Stage.FlatMapCompletionStage) {
          addStage(new FlatMapCompletionStage(GraphLogic.this, inlet, outlet, ((Stage.FlatMapCompletionStage) stage).getMapper()));
        } else if (stage instanceof Stage.FlatMapIterable) {
          addStage(new FlatMapIterableStage(GraphLogic.this, inlet, outlet, ((Stage.FlatMapIterable) stage).getMapper()));
        } else if (stage instanceof Stage.Processor) {
          Flow.Processor processor = ((Stage.Processor) stage).getProcessor();
          addStage(new ConnectorStage(GraphLogic.this, publisher, processor));
          addStage(new ConnectorStage(GraphLogic.this, processor, subscriber));
        } else {
          throw new UnsupportedStageException(stage);
        }

      // Outlets
      } else {
        if (stage instanceof Stage.Collect) {
          addStage(new CollectStage(GraphLogic.this, inlet, result, ((Stage.Collect) stage).getCollector()));
        } else if (stage instanceof Stage.FindFirst) {
          addStage(new FindFirstStage(GraphLogic.this, inlet, result));
        } else if (stage instanceof Stage.ForEach) {
          addStage(new ForEachStage(GraphLogic.this, inlet, result, ((Stage.ForEach) stage).getAction()));
        } else if (stage instanceof Stage.Subscriber) {
          if (inlet == null) {
            SubscriberInlet subscriberInlet = new SubscriberInlet(DEFAULT_BUFFER_HIGH_WATERMARK, DEFAULT_BUFFER_LOW_WATERMARK);
            builderPorts.add(subscriberInlet);
            addStage(new ConnectorStage(GraphLogic.this, publisher, subscriberInlet));
            inlet = subscriberInlet;
          }
          PublisherOutlet publisherOutlet = new PublisherOutlet();
          builderPorts.add(publisherOutlet);
          addStage(new CaptureTerminationStage(GraphLogic.this, inlet, publisherOutlet, result));
          addStage(new ConnectorStage(GraphLogic.this, publisherOutlet, ((Stage.Subscriber) stage).getSubscriber()));
        } else {
          throw new UnsupportedStageException(stage);
        }
      }
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
          execute(() -> {});
        }

      } catch (RuntimeException e) {
        // shut down the stream
        streamFailure(e);
        // Clear remaining signals
        unrolledSignals.clear();
      }
    });
  }

  private void startGraph() {
    execute(() -> {
      for (GraphStage stage: stages) {
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
   * A port, which may sit between two stages of this graph.
   */
  private interface Port {
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

  /**
   * A stage inlet.
   *
   * Stages may use this to interact with their inlet side.
   *
   * Stages may pull at most one element at a time. A listener must be registered
   * @param <T>
   */
  abstract class StageInlet<T> {
    private StageInlet() {
    }

    abstract void pull();

    abstract boolean isPulled();

    abstract boolean isAvailable();

    abstract boolean isFinished();

    abstract void finish();

    abstract T grab();

    abstract void setListener(InletListener listener);
  }

  final class SubStageInlet<T> extends StageInlet<T> {
    private final StageInlet<T> delegate;
    private final List<GraphStage> subStages;
    private final List<Port> subStagePorts;

    private SubStageInlet(StageInlet<T> delegate, List<GraphStage> subStages, List<Port> subStagePorts) {
      this.delegate = delegate;
      this.subStages = subStages;
      this.subStagePorts = subStagePorts;
    }

    void start() {
      subStagePorts.forEach(Port::verifyReady);
      subStages.forEach(GraphStage::postStart);
    }

    private void shutdown() {
      stages.removeAll(subStages);
      ports.removeAll(subStagePorts);
    }

    @Override
    void pull() {
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
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public void finish() {
      delegate.finish();
    }

    @Override
    public T grab() {
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

  interface InletListener {
    void onPush();

    void onUpstreamFinish();

    void onUpstreamFailure(Throwable error);
  }

  abstract class StageOutlet<T> {
    private StageOutlet() {
    }

    abstract void push(T element);

    abstract boolean isAvailable();

    abstract void finish();

    abstract boolean isFinished();

    abstract void fail(Throwable error);

    abstract void setListener(OutletListener listener);
  }

  interface OutletListener {
    void onPull();

    void onDownstreamFinish();
  }

  /**
   * An unrolled signal.
   * <p>
   * It is possible for stages to get into an infinite recursion, doing push/pulls between each other. This interface
   * allows them to unroll the recursion, by adding the signal to the unrolledSignals queue in this class, which then
   * gets executed after the first callback is executed.
   */
  private interface UnrolledSignal {
    void signal();
  }

  private final class StageOutletInlet<T> implements Port {
    private InletListener inletListener;
    private OutletListener outletListener;
    private boolean inletPulled;
    private T pushedElement;
    private T currentElement;
    private boolean outletFinished;
    private boolean inletFinished;
    private Throwable failure;

    @Override
    public void onStreamFailure(Throwable reason) {
      if (!outletFinished) {
        outletFinished = true;
        if (outletListener != null) {
          outletListener.onDownstreamFinish();
        }
      }
      if (!inletFinished) {
        inletFinished = true;
        if (inletListener != null) {
          inletListener.onUpstreamFailure(reason);
        }
      }
    }

    @Override
    public void verifyReady() {
      if (inletListener == null) {
        throw new IllegalStateException("Cannot start stream without inlet listener set");
      }
      if (outletListener == null) {
        throw new IllegalStateException("Cannot start stream without outlet listener set");
      }
    }

    final class Outlet extends StageOutlet<T> implements UnrolledSignal {
      @Override
      void push(T element) {
        Objects.requireNonNull(element, "Elements cannot be null");
        if (outletFinished) {
          throw new IllegalStateException("Can't push element after finish");
        } else if (!inletPulled || currentElement != null || pushedElement != null) {
          throw new IllegalStateException("Can't push element to outlet when it hasn't pulled");
        } else {
          pushedElement = element;
          unrolledSignals.add(this);
        }
      }

      @Override
      public void signal() {
        if (!inletFinished) {
          currentElement = pushedElement;
          pushedElement = null;
          inletListener.onPush();
          // Possible that there was a pull/push cycle done during that onPush,
          // followed by a finish, in which case, we don't want to publish that
          // finish yet.
          if (outletFinished && pushedElement == null && !inletFinished) {
            inletFinished = true;
            if (failure != null) {
              inletListener.onUpstreamFailure(failure);
              failure = null;
            } else {
              inletListener.onUpstreamFinish();
            }
          }
        }
      }

      @Override
      boolean isAvailable() {
        return !outletFinished && inletPulled && pushedElement == null && currentElement == null;
      }

      @Override
      void finish() {
        if (outletFinished) {
          throw new IllegalStateException("Can't finish twice.");
        }
        outletFinished = true;
        inletPulled = false;
        if (pushedElement == null && currentElement == null && !inletFinished) {
          inletFinished = true;
          inletListener.onUpstreamFinish();
        }
      }

      @Override
      boolean isFinished() {
        return outletFinished;
      }

      @Override
      void fail(Throwable error) {
        Objects.requireNonNull(error, "Error must not be null");
        if (outletFinished) {
          throw new IllegalStateException("Can't finish twice.");
        }
        outletFinished = true;
        inletPulled = false;
        if (pushedElement == null && currentElement == null && !inletFinished) {
          inletListener.onUpstreamFailure(error);
          inletFinished = true;
        } else {
          failure = error;
        }
      }

      @Override
      void setListener(OutletListener listener) {
        Objects.requireNonNull(listener, "Cannot register null listener");
        outletListener = listener;
      }
    }

    final class Inlet extends StageInlet<T> {

      @Override
      void pull() {
        if (inletFinished) {
          throw new IllegalStateException("Can't pull after finish");
        } else if (inletPulled) {
          throw new IllegalStateException("Can't pull twice");
        } else if (currentElement != null) {
          throw new IllegalStateException("Can't pull without having grabbed the previous element");
        }
        if (!outletFinished) {
          inletPulled = true;
          outletListener.onPull();
        }
      }

      @Override
      boolean isPulled() {
        return inletPulled;
      }

      @Override
      boolean isAvailable() {
        return currentElement != null;
      }

      @Override
      boolean isFinished() {
        return inletFinished;
      }

      @Override
      void finish() {
        if (inletFinished) {
          throw new IllegalStateException("Stage already finished");
        }
        inletFinished = true;
        currentElement = null;
        inletPulled = false;
        if (!outletFinished) {
          outletListener.onDownstreamFinish();
          outletFinished = true;
        }
      }

      @Override
      T grab() {
        if (currentElement == null) {
          throw new IllegalStateException("Grab without onPush notification");
        }
        T grabbed = currentElement;
        inletPulled = false;
        currentElement = null;
        return grabbed;
      }

      @Override
      void setListener(InletListener listener) {
        Objects.requireNonNull(listener, "Cannot register null listener");
        inletListener = listener;
      }
    }
  }

  private final class PublisherOutlet<T> extends StageOutlet<T> implements Flow.Publisher<T>, Flow.Subscription, Port, UnrolledSignal {

    private Flow.Subscriber<? super T> subscriber;
    private boolean pulled;
    private long demand;
    private boolean finished;
    private Throwable failure;
    private OutletListener listener;

    @Override
    public void onStreamFailure(Throwable reason) {
      if (!finished) {
        finished = true;
        demand = 0;
        if (subscriber != null) {
          try {
            subscriber.onError(reason);
          } catch (Exception e) {
            // Ignore
          }
        } else {
          failure = reason;
        }
        listener.onDownstreamFinish();
      }
    }

    @Override
    public void verifyReady() {
      if (listener == null) {
        throw new IllegalStateException("Cannot start stream without inlet listener set");
      }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
      Objects.requireNonNull(subscriber, "Subscriber must not be null");
      execute(() -> {
        if (this.subscriber != null) {
          subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
          });
          subscriber.onError(new IllegalStateException("This publisher only supports one subscriber"));
        } else {
          this.subscriber = subscriber;
          subscriber.onSubscribe(this);
          if (finished) {
            if (failure != null) {
              subscriber.onError(failure);
              failure = null;
              this.subscriber = null;
            } else {
              subscriber.onComplete();
              this.subscriber = null;
            }
          }
        }
      });
    }

    @Override
    public void request(long n) {
      execute(() -> {
        if (!finished) {
          if (n <= 0) {
            onStreamFailure(new IllegalArgumentException("Request demand must be greater than zero"));
          } else {
            boolean existingDemand = demand > 0;
            demand = demand + n;
            if (demand <= 0) {
              demand = Long.MAX_VALUE;
            }
            if (!existingDemand) {
              doPull();
            }
          }
        }
      });
    }

    @Override
    public void signal() {
      if (!finished && !pulled) {
        doPull();
      }
    }

    private void doPull() {
      pulled = true;
      listener.onPull();
    }

    @Override
    public void cancel() {
      execute(() -> {
        subscriber = null;
        if (!finished) {
          finished = true;
          demand = 0;
          listener.onDownstreamFinish();
        }
      });
    }

    @Override
    void push(T element) {
      Objects.requireNonNull(element, "Elements cannot be null");
      if (finished) {
        throw new IllegalStateException("Can't push after publisher is finished");
      } else if (demand <= 0) {
        throw new IllegalStateException("Push without pull");
      }
      pulled = false;
      if (demand != Long.MAX_VALUE) {
        demand -= 1;
      }
      subscriber.onNext(element);
      if (demand > 0) {
        unrolledSignals.add(this);
      }
    }

    @Override
    boolean isAvailable() {
      return !finished && pulled;
    }

    @Override
    void finish() {
      if (finished) {
        throw new IllegalStateException("Can't finish twice");
      } else {
        finished = true;
        demand = 0;
        if (subscriber != null) {
          subscriber.onComplete();
          subscriber = null;
        }
      }
    }

    @Override
    boolean isFinished() {
      return finished;
    }

    @Override
    void fail(Throwable error) {
      Objects.requireNonNull(error, "Error must not be null");
      if (finished) {
        throw new IllegalStateException("Can't finish twice");
      } else {
        finished = true;
        demand = 0;
        if (subscriber != null) {
          subscriber.onError(error);
          subscriber = null;
        } else {
          failure = error;
        }
      }
    }

    @Override
    void setListener(OutletListener listener) {
      Objects.requireNonNull(listener, "Listener must not be null");
      this.listener = listener;
    }
  }

  final class SubscriberInlet<T> extends StageInlet<T> implements Flow.Subscriber<T>, Port, UnrolledSignal {
    private final int bufferHighWatermark;
    private final int bufferLowWatermark;
    private final Deque<T> elements = new ArrayDeque<>();
    private T elementToPush;
    private Flow.Subscription subscription;
    private int outstandingDemand;
    private InletListener listener;
    private boolean upstreamFinished;
    private boolean downstreamFinished;
    private Throwable error;
    private boolean pulled;

    SubscriberInlet(int bufferHighWatermark, int bufferLowWatermark) {
      this.bufferHighWatermark = bufferHighWatermark;
      this.bufferLowWatermark = bufferLowWatermark;
    }

    @Override
    public void onStreamFailure(Throwable reason) {
      if (!upstreamFinished && subscription != null) {
        upstreamFinished = true;
        try {
          subscription.cancel();
        } catch (RuntimeException e) {
          // Ignore
        }
        subscription = null;
        if (!downstreamFinished) {
          downstreamFinished = true;
          listener.onUpstreamFailure(reason);
        }
      }
    }

    @Override
    public void verifyReady() {
      if (listener == null) {
        throw new IllegalStateException("Cannot start stream without inlet listener set");
      }
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      Objects.requireNonNull(subscription, "Subscription must not be null");
      execute(() -> {
        if (upstreamFinished || downstreamFinished || this.subscription != null) {
          subscription.cancel();
        } else {
          this.subscription = subscription;
          maybeRequest();
        }
      });
    }

    private void maybeRequest() {
      if (!upstreamFinished) {
        int bufferSize = outstandingDemand + elements.size();
        if (bufferSize <= bufferLowWatermark) {
          int toRequest = bufferHighWatermark - bufferSize;
          subscription.request(toRequest);
          outstandingDemand += toRequest;
        }
      }
    }

    @Override
    public void onNext(T item) {
      Objects.requireNonNull(item, "Elements passed to onNext must not be null");
      execute(() -> {
        if (downstreamFinished || upstreamFinished) {
          // Ignore events after cancellation or complete
        } else if (outstandingDemand == 0) {
          onStreamFailure(new IllegalStateException("Element signalled without demand for it"));
        } else {
          outstandingDemand -= 1;
          elements.add(item);
          if (pulled && elementToPush == null) {
            unrolledSignals.add(this);
          }
        }
      });
    }

    @Override
    public void signal() {
      if (!downstreamFinished) {
        if (!elements.isEmpty() && elementToPush == null) {
          elementToPush = elements.poll();
          listener.onPush();
        } else if (upstreamFinished) {
          downstreamFinished = true;
          if (error == null) {
            listener.onUpstreamFinish();
          } else {
            listener.onUpstreamFailure(error);
            error = null;
          }
        }
      }
    }

    @Override
    public void onError(Throwable throwable) {
      Objects.requireNonNull(throwable, "Error passed to onError must not be null");
      execute(() -> {
        if (downstreamFinished || upstreamFinished) {
          // Ignore
        } else {
          subscription = null;
          if (elements.isEmpty()) {
            downstreamFinished = true;
            upstreamFinished = true;
            listener.onUpstreamFailure(throwable);
          } else {
            upstreamFinished = true;
            error = throwable;
          }
        }
      });
    }

    @Override
    public void onComplete() {
      execute(() -> {
        if (downstreamFinished || upstreamFinished) {
          // Ignore
        } else {
          subscription = null;
          if (elements.isEmpty()) {
            downstreamFinished = true;
            upstreamFinished = true;
            listener.onUpstreamFinish();
          } else {
            upstreamFinished = true;
          }
        }
      });
    }

    @Override
    void pull() {
      if (downstreamFinished) {
        throw new IllegalStateException("Can't pull when finished");
      } else if (pulled) {
        throw new IllegalStateException("Can't pull twice");
      }
      pulled = true;
      if (!elements.isEmpty()) {
        unrolledSignals.add(this);
      }
    }

    @Override
    boolean isPulled() {
      return pulled;
    }

    @Override
    boolean isAvailable() {
      return !elements.isEmpty();
    }

    @Override
    boolean isFinished() {
      return downstreamFinished;
    }

    @Override
    void finish() {
      if (downstreamFinished) {
        throw new IllegalStateException("Can't finish twice");
      } else {
        downstreamFinished = true;
        upstreamFinished = true;
        error = null;
        elements.clear();
        if (subscription != null) {
          subscription.cancel();
          subscription = null;
        }
      }
    }

    @Override
    T grab() {
      if (downstreamFinished) {
        throw new IllegalStateException("Can't grab when finished");
      } else if (!pulled) {
        throw new IllegalStateException("Can't grab when not pulled");
      } else if (elementToPush == null) {
        throw new IllegalStateException("Grab without onPush");
      } else {
        pulled = false;
        T element = elementToPush;
        elementToPush = null;
        // Signal another signal so that we can notify downstream finish after
        // it gets the element without pulling first.
        if (elements.isEmpty() && upstreamFinished) {
          unrolledSignals.add(this);
        } else {
          maybeRequest();
        }
        return element;
      }
    }

    @Override
    void setListener(InletListener listener) {
      this.listener = listener;
    }
  }

}
