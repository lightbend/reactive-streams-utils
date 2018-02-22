/************************************************************************
 * Licensed under Public Domain (CC0)                                    *
 *                                                                       *
 * To the extent possible under law, the person who associated CC0 with  *
 * this code has waived all copyright and related or neighboring         *
 * rights to this code.                                                  *
 *                                                                       *
 * You should have received a copy of the CC0 legalcode along with this  *
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.*
 ************************************************************************/

package com.lightbend.streams.spi;

import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

/**
 * A stage of a Reactive Streams graph.
 *
 * A Reactive Streams engine will walk a graph of stages to produce {@link Publisher}, {@link Subscriber} and
 * {@link Processor} instances that handle the stream according to the sequence of stages.
 */
public interface Stage {

  /**
   * Whether this stage has an inlet - ie, when built, will it implement the {@link Subscriber} interface?
   *
   * @return True if this stage has an inlet.
   */
  default boolean hasInlet() {
    return false;
  }

  /**
   * Whether this stage has an outlet - ie, when built, will it implement the {@link Publisher} interface?
   *
   * @return True if this stage has an outlet.
   */
  default boolean hasOutlet() {
    return false;
  }

  /**
   * Convenience interface for inlet stages.
   */
  interface Inlet extends Stage {
    @Override
    default boolean hasInlet() {
      return true;
    }
  }

  /**
   * Convenience interface for outlet stages.
   */
  interface Outlet extends Stage {
    @Override
    default boolean hasOutlet() {
      return true;
    }
  }

  /**
   * A map stage.
   *
   * The given mapper function should be invoked on each element consumed, and the output of the function should be
   * emitted.
   *
   * Any {@link RuntimeException} thrown by the function should be propagated down the stream as an error.
   */
  final class Map implements Inlet, Outlet {
    private final Function<?, ?> mapper;

    public Map(Function<?, ?> mapper) {
      this.mapper = mapper;
    }

    /**
     * The mapper function.
     *
     * @return The mapper function.
     */
    public Function<?, ?> getMapper() {
      return mapper;
    }
  }

  /**
   * A filter stage.
   *
   * The given predicate should be invoked on each element consumed, and if it returns true, the element should be
   * emitted.
   *
   * Any {@link RuntimeException} thrown by the function should be propagated down the stream as an error.
   */
  final class Filter implements Inlet, Outlet {
    private final Predicate<?> predicate;

    public Filter(Predicate<?> predicate) {
      this.predicate = predicate;
    }

    /**
     * The predicate.
     *
     * @return The predicate.
     */
    public Predicate<?> getPredicate() {
      return predicate;
    }
  }

  /**
   * A publisher stage.
   *
   * The given publisher should be subscribed to whatever subscriber is provided to this graph, via any other subsequent
   * stages.
   */
  final class Publisher implements Outlet {
    private final Flow.Publisher<?> publisher;

    public Publisher(Flow.Publisher<?> publisher) {
      this.publisher = publisher;
    }

    /**
     * The publisher.
     *
     * @return The publisher.
     */
    public Flow.Publisher<?> getPublisher() {
      return publisher;
    }
  }

  /**
   * A single element publisher stage.
   *
   * When built, should produce a publisher that emits the single element, then completes the stream.
   */
  final class OfSingle implements Outlet {
    private final Object element;

    public OfSingle(Object element) {
      this.element = element;
    }

    /**
     * The element to emit.
     *
     * @return The element.
     */
    public Object getElement() {
      return element;
    }
  }

  /**
   * An empty publisher stage.
   *
   * When built, should produce a publisher that simply completes the stream.
   */
  final class Empty implements Outlet {
    private Empty() {
    }

    public static final Empty INSTANCE = new Empty();
  }

  /**
   * A publisher of many values.
   *
   * When built, should produce a publisher that produces all the values (until cancelled) emitted by this iterables
   * iterator, followed by completion of the stream.
   *
   * Any exceptions thrown by the iterator must be propagated downstream.
   */
  final class OfMany implements Outlet {
    private final java.lang.Iterable<?> elements;

    public OfMany(java.lang.Iterable<?> elements) {
      this.elements = elements;
    }

    /**
     * The elements to emit.
     *
     * @return The elements to emit.
     */
    public java.lang.Iterable<?> getElements() {
      return elements;
    }
  }

  /**
   * A processor stage.
   *
   * When built, should connect upstream of the graph to the inlet of this processor, and downstream to the outlet.
   */
  final class Processor implements Inlet, Outlet {
    private final Flow.Processor<?, ?> processor;

    public Processor(Flow.Processor<?, ?> processor) {
      this.processor = processor;
    }

    /**
     * The processor.
     *
     * @return The processor.
     */
    public Flow.Processor<?, ?> getProcessor() {
      return processor;
    }
  }

  /**
   * A subscriber stage that emits the first element encountered.
   *
   * When built, the {@link java.util.concurrent.CompletionStage} should emit an {@link java.util.Optional} of the first
   * element emitted. If no element is emitted before completion of the stream, it should emit an empty optional. Once
   * the element has been emitted, the stream should be cancelled if not already complete.
   *
   * If an error is emitted before the first element is encountered, the stream must redeem the completion stage with
   * that error.
   */
  final class FindFirst implements Inlet {
    private FindFirst() { }

    public static final FindFirst INSTANCE = new FindFirst();
  }

  /**
   * A subscriber.
   *
   * When built, the {@link java.util.concurrent.CompletionStage} should emit <code>null</code> when the stream
   * completes normally, or an error if the stream terminates with an error.
   *
   * Implementing this will typically require inserting a handler before the subscriber that listens for errors.
   */
  final class Subscriber implements Inlet {
    private final Flow.Subscriber<?> subscriber;

    public Subscriber(Flow.Subscriber<?> subscriber) {
      this.subscriber = subscriber;
    }

    /**
     * The subscriber.
     *
     * @return The subscriber.
     */
    public Flow.Subscriber<?> getSubscriber() {
      return subscriber;
    }
  }

  /**
   * A collect stage.
   *
   * This should use the collectors supplier to create an accumulated value, and then the accumulator BiConsumer should
   * be used to accumulate the received elements in the value. Finally, the returned
   * {@link java.util.concurrent.CompletionStage} should be redeemed by value returned by the finisher function applied
   * to the accumulated value when the stream terminates normally, or should be redeemed with an error if the stream
   * terminates with an error.
   *
   * If the collector throws an exception, the stream must be cancelled, and the
   * {@link java.util.concurrent.CompletionStage} must be redeemed with that error.
   */
  final class Collect implements Inlet {
    private final Collector<?, ?, ?> collector;

    public Collect(Collector<?, ?, ?> collector) {
      this.collector = collector;
    }

    public Collector<?, ?, ?> getCollector() {
      return collector;
    }
  }

  /**
   * A failed publisher.
   *
   * When built, this should produce a publisher that immediately fails the stream with the passed in error.
   */
  final class Failed implements Outlet {
    private final Throwable error;

    public Failed(Throwable error) {
      this.error = error;
    }

    public Throwable getError() {
      return error;
    }
  }

}
