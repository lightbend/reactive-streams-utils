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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Flow.*;

/**
 * Primary entry point into the Reactive Streams utility API.
 *
 * This class provides factory methods for publisher and processor builders, which can then be subsequently manipulated
 * using their respective APIs.
 */
public class ReactiveStreams {

  /**
   * Create a {@link PublisherBuilder} from the given {@link Publisher}.
   *
   * @param publisher The publisher to wrap.
   * @param <T>       The type of the elements that the publisher produces.
   * @return A publisher builder that wraps the given publisher.
   */
  public static <T> PublisherBuilder<T> fromPublisher(Publisher<? extends T> publisher) {
    return new PublisherBuilder<>(new Stage.Publisher(publisher), null);
  }

  /**
   * Create a {@link PublisherBuilder} that emits a single element.
   *
   * @param t   The element to emit.
   * @param <T> The type of the element.
   * @return A publisher builder that will emit the element.
   */
  public static <T> PublisherBuilder<T> of(T t) {
    return new PublisherBuilder<>(new Stage.Of(List.of(t)), null);
  }

  /**
   * Create a {@link PublisherBuilder} that emits the given elements.
   *
   * @param ts  The elements to emit.
   * @param <T> The type of the elements.
   * @return A publisher builder that will emit the elements.
   */
  public static <T> PublisherBuilder<T> of(T... ts) {
    return fromIterable(List.of(ts));
  }

  /**
   * Create an empty {@link PublisherBuilder}.
   *
   * @param <T> The type of the publisher builder.
   * @return A publisher builder that will just emit a completion signal.
   */
  public static <T> PublisherBuilder<T> empty() {
    return new PublisherBuilder<>(Stage.Of.EMPTY, null);
  }

  /**
   * Create a {@link PublisherBuilder} that will emit a single element if <code>t</code> is not null, otherwise will be
   * empty.
   *
   * @param t   The element to emit, <code>null</code> if to element should be emitted.
   * @param <T> The type of the element.
   * @return A publisher builder that optionally emits a single element.
   */
  public static <T> PublisherBuilder<T> ofNullable(T t) {
    return t == null ? empty() : of(t);
  }

  /**
   * Create a {@link PublisherBuilder} that will emits the elements produced by the passed in {@link Iterable}.
   *
   * @param ts  The elements to emit.
   * @param <T> The type of the elements.
   * @return A publisher builder that emits the elements of the iterable.
   */
  public static <T> PublisherBuilder<T> fromIterable(Iterable<? super T> ts) {
    return new PublisherBuilder<>(new Stage.Of(ts), null);
  }

  /**
   * Create a failed {@link PublisherBuilder}.
   * <p>
   * This publisher will just emit an error.
   *
   * @param t   The error te emit.
   * @param <T> The type of the publisher builder.
   * @return A publisher builder that completes the stream with an error.
   */
  public static <T> PublisherBuilder<T> failed(Throwable t) {
    return new PublisherBuilder<>(new Stage.Failed(t), null);
  }

  /**
   * Create a {@link ProcessorBuilder}. This builder will start as an identity processor.
   *
   * @param <T> The type of elements that the processor consumes and emits.
   * @return The identity processor builder.
   */
  public static <T> ProcessorBuilder<T, T> builder() {
    return new ProcessorBuilder<>(InternalStages.Identity.INSTANCE, null);
  }

  /**
   * Create a {@link ProcessorBuilder} from the given {@link Processor}.
   *
   * @param processor The processor to be wrapped.
   * @param <T>       The type of the elements that the processor consumes.
   * @param <R>       The type of the elements that the processor emits.
   * @return A processor builder that wraps the processor.
   */
  public static <T, R> ProcessorBuilder<T, R> fromProcessor(Processor<? super T, ? extends R> processor) {
    return new ProcessorBuilder<>(new Stage.Processor(processor), null);
  }

  /**
   * Create a {@link SubscriberBuilder} from the given {@link Subscriber}.
   *
   * @param subscriber The subscriber to be wrapped.
   * @param <T>        The type of elements that the subscriber consumes.
   * @return A subscriber builder that wraps the subscriber.
   */
  public static <T> SubscriberBuilder<T, Void> fromSubscriber(Subscriber<? extends T> subscriber) {
    return new SubscriberBuilder<>(new Stage.Subscriber(subscriber), null);
  }
}
