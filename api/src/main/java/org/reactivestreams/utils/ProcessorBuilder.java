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

package org.reactivestreams.utils;

import org.reactivestreams.utils.spi.Stage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * A builder for a {@link Processor}.
 *
 * @param <T> The type of the elements that the processor consumes.
 * @param <R> The type of the elements that the processor emits.
 * @see ReactiveStreams
 */
public final class ProcessorBuilder<T, R> extends ReactiveStreamsBuilder<Processor<T, R>> {

  ProcessorBuilder(Stage stage, ReactiveStreamsBuilder<?> previous) {
    super(stage, previous);
  }

  /**
   * Map the elements emitted by this processor using the <code>mapper</code> function.
   *
   * @param mapper The function to use to map the elements.
   * @param <S>    The type of elements that the <code>mapper</code> function emits.
   * @return A new processor builder that consumes elements of type <code>T</code> and emits the mapped elements.
   */
  public <S> ProcessorBuilder<T, S> map(Function<? super R, ? extends S> mapper) {
    return new ProcessorBuilder<>(new Stage.Map(mapper), this);
  }

  /**
   * Filter elements emitted by this processor using the given {@link Predicate}.
   * <p>
   * Any elements that return <code>true</code> when passed to the {@link Predicate} will be emitted, all other
   * elements will be dropped.
   *
   * @param predicate The predicate to apply to each element.
   * @return A new processor builder.
   */
  public ProcessorBuilder<T, R> filter(Predicate<? super R> predicate) {
    return new ProcessorBuilder<>(new Stage.Filter(predicate), this);
  }

  /**
   * Find the first element emitted by the {@link Processor}, and return it in a
   * {@link java.util.concurrent.CompletionStage}.
   * <p>
   * If the stream is completed before a single element is emitted, then {@link Optional#empty()} will be emitted.
   *
   * @return A {@link SubscriberBuilder} that represents this processor builders inlet.
   */
  public SubscriberBuilder<T, Optional<R>> findFirst() {
    return new SubscriberBuilder<>(Stage.FindFirst.INSTANCE, this);
  }

  /**
   * Collect the elements emitted by this processor builder using the given {@link Collector}.
   * <p>
   * Since Reactive Streams are intrinsically sequential, only the accumulator of the collector will be used, the
   * combiner will not be used.
   *
   * @param collector The collector to collect the elements.
   * @param <S>       The result of the collector.
   * @param <A>       The accumulator type.
   * @return A {@link SubscriberBuilder} that represents this processor builders inlet.
   */
  public <S, A> SubscriberBuilder<T, S> collect(Collector<? super R, A, S> collector) {
    return new SubscriberBuilder<>(new Stage.Collect(collector), this);
  }

  /**
   * Collect the elements emitted by this processor builder into a {@link List}
   *
   * @return A {@link SubscriberBuilder} that represents this processor builders inlet.
   */
  public SubscriberBuilder<T, List<R>> toList() {
    return collect(Collectors.toList());
  }

  /**
   * Connect the outlet of the {@link Processor} built by this builder to the given {@link Subscriber}.
   *
   * @param subscriber The subscriber to connect.
   * @return A {@link SubscriberBuilder} that represents this processor builders inlet.
   */
  public SubscriberBuilder<T, Void> to(Subscriber<R> subscriber) {
    return new SubscriberBuilder<>(new Stage.Subscriber(subscriber), this);
  }

  /**
   * Connect the outlet of this processor builder to the given {@link SubscriberBuilder} graph.
   *
   * @param subscriber The subscriber builder to connect.
   * @return A {@link SubscriberBuilder} that represents this processor builders inlet.
   */
  public <S> SubscriberBuilder<T, S> to(SubscriberBuilder<R, S> subscriber) {
    return new SubscriberBuilder<>(new InternalStages.Nested(subscriber), this);
  }

  /**
   * Connect the outlet of the {@link Processor} built by this builder to the given {@link Processor}.
   *
   * @param processor The processor to connect.
   * @return A {@link ProcessorBuilder} that represents this processor builders inlet, and the passed in processors
   * outlet.
   */
  public <S> ProcessorBuilder<T, S> via(ProcessorBuilder<R, S> processor) {
    return new ProcessorBuilder<>(new InternalStages.Nested(processor), this);
  }

  /**
   * Connect the outlet of this processor builder to the given {@link ProcessorBuilder} graph.
   *
   * @param processor The processor builder to connect.
   * @return A {@link ProcessorBuilder} that represents this processor builders inlet, and the passed in
   * processor builders outlet.
   */
  public <S> ProcessorBuilder<T, S> via(Processor<R, S> processor) {
    return new ProcessorBuilder<>(new Stage.Processor(processor), this);
  }

  @Override
  public Processor<T, R> build(ReactiveStreamsEngine engine) {
    return engine.buildProcessor(verify(flatten(), true, true));
  }
}
