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

/**
 * Reactive streams support.
 * <p>
 * This provides utilities for building stream graphs that consume or produce elements using the
 * {@link java.util.concurrent.Flow.Publisher}, {@link java.util.concurrent.Flow.Subscriber} and
 * {@link java.util.concurrent.Flow.Processor} interfaces.
 * <p>
 * There are four primary classes used for building these graphs:
 * <p>
 * <ul>
 * <li>{@link PublisherBuilder}, which when built produces a {@link java.util.concurrent.Flow.Publisher}</li>
 * <li>{@link SubscriberBuilder}, which when built produces a {@link SubscriberWithResult}</li>
 * <li>{@link ProcessorBuilder}, which when built produces a {@link java.util.concurrent.Flow.Processor}</li>
 * <li>{@link CompletionBuilder}, which when built produces a {@link java.util.concurrent.CompletionStage}</li>
 * </ul>
 * <p>
 * Operations on these builders may change the shape of the builder, for example, {@link ProcessorBuilder#toList()}
 * changes the builder to a {@link SubscriberBuilder}, since the processor now has a termination stage to direct its
 * elements to.
 * <p>
 * {@link SubscriberBuilder}'s are a special case, in that they don't just build a
 * {@link java.util.concurrent.Flow.Subscriber}, they build a {@link SubscriberWithResult}, which encapsulates both a
 * {@link java.util.concurrent.Flow.Subscriber} and a {@link java.util.concurrent.CompletionStage} of the result of the
 * subscriber processing. This {@link java.util.concurrent.CompletionStage} will be redeemed with a result when the
 * stream terminates normally, or if the stream terminates with an error, will be redeemed with an error. The result is
 * specific to whatever the {@link java.util.concurrent.Flow.Subscriber} does, for example, in the case of
 * {@link ProcessorBuilder#toList()}, the result will be a {@link java.util.List} of all the elements produced by the
 * {@link java.util.concurrent.Flow.Processor}, while in the case of {@link ProcessorBuilder#findFirst()}, it's an
 * {@link java.util.Optional} of the first element of the stream. In some cases, there is no result, in which case the
 * result is the {@link Void} type, and the {@link java.util.concurrent.CompletionStage} is only useful for signalling
 * normal or error termination of the stream.
 * <p>
 * The {@link CompletionBuilder} builds a closed graph, in that case both a {@link java.util.concurrent.Flow.Publisher}
 * and {@link java.util.concurrent.Flow.Subscriber} have been provided, and building the graph will run it and return
 * the result of the {@link java.util.concurrent.Flow.Subscriber} as a {@link java.util.concurrent.CompletionStage}.
 * <p>
 * An example use of this API is perhaps you have a {@link java.util.concurrent.Flow.Publisher} of rows from a database,
 * and you want to output it as a comma separated list of lines to publish to an HTTP client request body, which
 * expects a {@link java.util.concurrent.Flow.Publisher} of {@link java.nio.ByteBuffer}. Here's how this might be implemented:
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
package org.reactivestreams.utils;