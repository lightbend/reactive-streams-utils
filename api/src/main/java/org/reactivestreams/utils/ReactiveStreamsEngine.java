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
   * The following invariant will always hold true for the stages - every stage will have an outlet, while the first
   * stage will have no inlet, but every subsequent stage will have an inlet.
   *
   * @param stages The stages to build the publisher from. Will not be empty.
   * @param <T>    The type of elements that the publisher publishes.
   * @return A publisher that implements the passed in graph of stages.
   * @throws UnsupportedStageException If a stage in the stages is not supported by this Reactive Streams engine.
   */
  <T> Publisher<T> buildPublisher(Iterable<Stage> stages) throws UnsupportedStageException;

  /**
   * Build a {@link Subscriber} from the given stages.
   * <p>
   * The following invariant will always hold true for the stages - every stage will have an inlet, while the last
   * stage will have no outlet, but every prior stage will have an outlet.
   *
   * @param stages The stages to build the subscriber from. Will not be empty.
   * @param <T>    The type of elements that the subscriber subscribes to.
   * @param <R>    The result of subscribing to the stages.
   * @return A subscriber that implements the passed in graph of stages.
   * @throws UnsupportedStageException If a stage in the stages is not supported by this Reactive Streams engine.
   */
  <T, R> SubscriberWithResult<T, R> buildSubscriber(Iterable<Stage> stages) throws UnsupportedStageException;

  /**
   * Build a {@link Processor} from the given stages.
   * <p>
   * The following invariant will always hold true for the stages - every stage will have both an inlet and an outlet.
   *
   * @param stages The stages to build the processor from. If empty, then the processor should be an identity processor.
   * @param <T>    The type of elements that the processor subscribes to.
   * @param <R>    The type of elements that the processor publishes.
   * @return A processor that implements the passed in graph of stages.
   * @throws UnsupportedStageException If a stage in the stages is not supported by this Reactive Streams engine.
   */
  <T, R> Processor<T, R> buildProcessor(Iterable<Stage> stages) throws UnsupportedStageException;

  /**
   * Build a closed graph from the given stages.
   * <p>
   * The following invariant will always hold true for the stages - the first stage will have no inlet but will have
   * an outlet, the last stage will have an inlet but no outlet, and every other stage will have both an inlet and an
   * outlet.
   *
   * @param stages The stages to build the closed graph from. Will not be empty.
   * @param <T>    The type of the result of running the closed graph.
   * @return A CompletionStage of the result of running the graph.
   * @throws UnsupportedStageException If a stage in the stages is not supported by this reactive streams engine.
   */
  <T> CompletionStage<T> buildCompletion(Iterable<Stage> stages) throws UnsupportedStageException;

}
