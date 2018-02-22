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
import java.util.concurrent.Flow.*;

/**
 * Reactive streams support.
 * <p>
 * This provides utilities for building stream graphs that consume or produce elements using the {@link Publisher},
 * {@link Subscriber} and {@link Processor} interfaces.
 * <p>
 * There are four primary classes used for building these graphs:
 * <p>
 * <ul>
 * <li>{@link PublisherBuilder}, which when built produces a {@link Publisher}</li>
 * <li>{@link SubscriberBuilder}, which when built produces a {@link SubscriberWithResult}</li>
 * <li>{@link ProcessorBuilder}, which when built produces a {@link Processor}</li>
 * <li>{@link CompletionBuilder}, which when built produces a {@link java.util.concurrent.CompletionStage}</li>
 * </ul>
 * <p>
 * Operations on these builders may change the shape of the builder, for example, {@link ProcessorBuilder#toList()}
 * changes the builder to a {@link SubscriberBuilder}, since the processor now has a termination stage to direct its
 * elements to.
 * <p>
 * {@link SubscriberBuilder}'s are a special case, in that they don't just build a {@link Subscriber}, they build a
 * {@link SubscriberWithResult}, which encapsulates both a {@link Subscriber} and a
 * {@link java.util.concurrent.CompletionStage} of the result of the subscriber processing. This
 * {@link java.util.concurrent.CompletionStage} will be redeemed with a result when the stream terminates normally, or
 * if the stream terminates with an error, will be redeemed with an error. The result is specific to whatever the
 * {@link Subscriber} does, for example, in the case of {@link ProcessorBuilder#toList()}, the result will be a
 * {@link java.util.List} of all the elements produced by the {@link Processor}, while in the case of
 * {@link ProcessorBuilder#findFirst()}, it's an {@link java.util.Optional} of the first element of the stream. In some
 * cases, there is no result, in which case the result is the {@link Void} type, and the
 * {@link java.util.concurrent.CompletionStage} is only useful for signalling normal or error termination of the stream.
 * <p>
 * The {@link CompletionBuilder} builds a closed graph, in that case both a {@link Publisher} and {@link Subscriber}
 * have been provided, and building the graph will run it and return the result of the {@link Subscriber} as a
 * {@link java.util.concurrent.CompletionStage}.
 * <p>
 * An example use of this API is perhaps you have a {@link Publisher} of rows from a database, and you want to output
 * it as a comma separated list of lines to publish to an HTTP client request body, which expects a {@link Publisher}
 * of {@link java.nio.ByteBuffer}. Here's how this might be implemented:
 * <p>
 * <pre>
 *   Publisher&lt;Row&gt; rowsPublisher = ...;
 *
 *   Publisher&lt;ByteBuffer&gt; bodyPublisher =
 *     // Create a publisher builder from the rows publisher
 *     ReactiveStreams.fromPublisher(rowsPublisher)
 *       // Map the rows to CSV strings
 *       .map(row -&gt;
 *         String.format("%s, %s, %d\n", row.getString("firstName"),
 *           row.getString("lastName"), row.getInt("age))
 *       )
 *       // Convert to ByteBuffer
 *       .map(line -&gt; ByteBuffer.wrap(line.getBytes("utf-8")))
 *       // Build the publisher
 *       .build();
 *
 *   // Make the request
 *   HttpClient client = HttpClient.newHttpClient();
 *   client.send(
 *     HttpRequest
 *       .newBuilder(new URI("http://www.foo.com/"))
 *       .POST(BodyProcessor.fromPublisher(bodyPublisher))
 *       .build()
 *   );
 * </pre>
 */
public class ReactiveStreams {

  /**
   * Create a {@link PublisherBuilder} from the given {@link Publisher}.
   *
   * @param publisher The publisher to wrap.
   * @param <T>       The type of the elements that the publisher produces.
   * @return A publisher builder that wraps the given publisher.
   */
  public static <T> PublisherBuilder<T> fromPublisher(Publisher<? super T> publisher) {
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
    return new PublisherBuilder<>(new Stage.OfSingle(t), null);
  }

  /**
   * Create a {@link PublisherBuilder} that emits the given elements.
   *
   * @param ts  The elements to emit.
   * @param <T> The type of the elements.
   * @return A publisher builder that will emit the elements.
   */
  public static <T> PublisherBuilder<T> of(T... ts) {
    return fromIterable(Arrays.asList(ts));
  }

  /**
   * Create an empty {@link PublisherBuilder}.
   *
   * @param <T> The type of the publisher builder.
   * @return A publisher builder that will just emit a completion signal.
   */
  public static <T> PublisherBuilder<T> empty() {
    return new PublisherBuilder<>(Stage.Empty.INSTANCE, null);
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
    return new PublisherBuilder<>(new Stage.OfMany(ts), null);
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
