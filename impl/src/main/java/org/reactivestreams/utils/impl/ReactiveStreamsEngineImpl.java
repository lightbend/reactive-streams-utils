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

import org.reactivestreams.utils.ReactiveStreamsEngine;
import org.reactivestreams.utils.SubscriberWithResult;
import org.reactivestreams.utils.spi.Graph;
import org.reactivestreams.utils.spi.UnsupportedStageException;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;

/**
 * Implementation of the reactive streams engine.
 */
public class ReactiveStreamsEngineImpl implements ReactiveStreamsEngine {
  @Override
  public <T> Flow.Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
    return BuiltGraph.buildPublisher(ForkJoinPool.commonPool(), graph);
  }

  @Override
  public <T, R> SubscriberWithResult<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
    return BuiltGraph.buildSubscriber(ForkJoinPool.commonPool(), graph);
  }

  @Override
  public <T, R> Flow.Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
    return BuiltGraph.buildProcessor(ForkJoinPool.commonPool(), graph);
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
    return BuiltGraph.buildCompletion(ForkJoinPool.commonPool(), graph);
  }
}
