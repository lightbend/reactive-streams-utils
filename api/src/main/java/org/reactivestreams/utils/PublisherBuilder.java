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
 * A builder for a {@link Publisher}.
 *
 * @param <T> The type of the elements that the publisher emits.
 * @see ReactiveStreams
 */
public final class PublisherBuilder<T> extends ReactiveStreamsBuilder<Publisher<T>> {

  PublisherBuilder(Stage stage, ReactiveStreamsBuilder<?> previous) {
    super(stage, previous);
  }

  /**
   * Map the elements emitted by this publisher using the <code>mapper</code> function.
   *
   * @param mapper The function to use to map the elements.
   * @param <R>    The type of elements that the <code>mapper</code> function emits.
   * @return A new publisher builder that emits the mapped elements.
   */
  public <R> PublisherBuilder<R> map(Function<? super T, ? extends R> mapper) {
    return new PublisherBuilder<>(new Stage.Map(mapper), this);
  }

  /**
   * Filter elements emitted by this publisher using the given {@link Predicate}.
   * <p>
   * Any elements that return <code>true</code> when passed to the {@link Predicate} will be emitted, all other
   * elements will be dropped.
   *
   * @param predicate The predicate to apply to each element.
   * @return A new publisher builder.
   */
  public PublisherBuilder<T> filter(Predicate<? super T> predicate) {
    return new PublisherBuilder<>(new Stage.Filter(predicate), this);
  }

  /**
   * Find the first element emitted by the {@link Publisher}, and return it in a
   * {@link java.util.concurrent.CompletionStage}.
   * <p>
   * If the stream is completed before a single element is emitted, then {@link Optional#empty()} will be emitted.
   *
   * @return A {@link CompletionBuilder} that emits the element when found.
   */
  public CompletionBuilder<Optional<T>> findFirst() {
    return new CompletionBuilder<>(Stage.FindFirst.INSTANCE, this);
  }

  /**
   * Collect the elements emitted by this publisher builder using the given {@link Collector}.
   * <p>
   * Since Reactive Streams are intrinsically sequential, only the accumulator of the collector will be used, the
   * combiner will not be used.
   *
   * @param collector The collector to collect the elements.
   * @param <R>       The result of the collector.
   * @param <A>       The accumulator type.
   * @return A {@link CompletionBuilder} that emits the collected result.
   */
  public <R, A> CompletionBuilder<R> collect(Collector<? super T, A, R> collector) {
    return new CompletionBuilder<>(new Stage.Collect(collector), this);
  }

  /**
   * Collect the elements emitted by this publisher builder into a {@link List}
   *
   * @return A {@link CompletionBuilder} that emits the list.
   */
  public CompletionBuilder<List<T>> toList() {
    return collect(Collectors.toList());
  }

  /**
   * Connect the outlet of the {@link Publisher} built by this builder to the given {@link Subscriber}.
   *
   * @param subscriber The subscriber to connect.
   * @return A {@link CompletionBuilder} that completes when the stream completes.
   */
  public CompletionBuilder<Void> to(Subscriber<T> subscriber) {
    return new CompletionBuilder<>(new Stage.Subscriber(subscriber), this);
  }

  /**
   * Connect the outlet of this publisher builder to the given {@link SubscriberBuilder} graph.
   *
   * @param subscriber The subscriber builder to connect.
   * @return A {@link CompletionBuilder} that completes when the stream completes.
   */
  public <R> CompletionBuilder<R> to(SubscriberBuilder<T, R> subscriber) {
    return new CompletionBuilder<>(new InternalStages.Nested(subscriber), this);
  }

  /**
   * Connect the outlet of the {@link Publisher} built by this builder to the given {@link Processor}.
   *
   * @param processor The processor to connect.
   * @return A {@link PublisherBuilder} that represents the passed in processors outlet.
   */
  public <R> PublisherBuilder<R> via(ProcessorBuilder<T, R> processor) {
    return new PublisherBuilder<>(new InternalStages.Nested(processor), this);
  }

  /**
   * Connect the outlet of this publisher builder to the given {@link ProcessorBuilder} graph.
   *
   * @param processor The processor builder to connect.
   * @return A {@link PublisherBuilder} that represents the passed in processor builders outlet.
   */
  public <R> PublisherBuilder<R> via(Processor<T, R> processor) {
    return new PublisherBuilder<>(new Stage.Processor(processor), this);
  }

  @Override
  public Publisher<T> build(ReactiveStreamsEngine engine) {
    return engine.buildPublisher(verify(flatten(), false, true));
  }
}
