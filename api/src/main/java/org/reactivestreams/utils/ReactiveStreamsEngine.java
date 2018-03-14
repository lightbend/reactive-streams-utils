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

import org.reactivestreams.utils.spi.Graph;
import org.reactivestreams.utils.spi.UnsupportedStageException;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.*;

/**
 * An engine for turning reactive streams graphs into {@link java.util.concurrent.Flow} publishers/subscribers.
 * <p>
 * The zero argument {@link ReactiveStreamsBuilder#build()} method will use the {@link java.util.ServiceLoader} to load
 * an engine for the current context classloader. It does not cache engines between invocations. If instantiating an
 * engine is expensive (eg, it creates threads), then it is recommended that the implementation does its own caching
 * by providing the engine using a static provider method.
 */
public interface ReactiveStreamsEngine {

  /**
   * Build a {@link Publisher} from the given stages.
   * <p>
   * The passed in graph will have an outlet and no inlet.
   *
   * @param graph The stages to build the publisher from. Will not be empty.
   * @param <T>   The type of elements that the publisher publishes.
   * @return A publisher that implements the passed in graph of stages.
   * @throws UnsupportedStageException If a stage in the stages is not supported by this Reactive Streams engine.
   */
  <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException;

  /**
   * Build a {@link Subscriber} from the given stages.
   * <p>
   * The passed in graph will have an inlet and no outlet.
   *
   * @param graph The graph to build the subscriber from. Will not be empty.
   * @param <T>   The type of elements that the subscriber subscribes to.
   * @param <R>   The result of subscribing to the stages.
   * @return A subscriber that implements the passed in graph of stages.
   * @throws UnsupportedStageException If a stage in the stages is not supported by this Reactive Streams engine.
   */
  <T, R> SubscriberWithResult<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException;

  /**
   * Build a {@link Processor} from the given stages.
   * <p>
   * The passed in graph will have an inlet and an outlet.
   *
   * @param graph The graph to build the processor from. If empty, then the processor should be an identity processor.
   * @param <T>   The type of elements that the processor subscribes to.
   * @param <R>   The type of elements that the processor publishes.
   * @return A processor that implements the passed in graph of stages.
   * @throws UnsupportedStageException If a stage in the stages is not supported by this Reactive Streams engine.
   */
  <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException;

  /**
   * Build a closed graph from the given stages.
   * <p>
   * The passed in graph will have no inlet and no outlet.
   *
   * @param graph The graph to build the closed graph from. Will not be empty.
   * @param <T>   The type of the result of running the closed graph.
   * @return A CompletionStage of the result of running the graph.
   * @throws UnsupportedStageException If a stage in the stages is not supported by this reactive streams engine.
   */
  <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException;

}
