package org.reactivestreams.utils.impl;

import org.reactivestreams.utils.ReactiveStreamsEngine;
import org.reactivestreams.utils.SubscriberWithResult;
import org.reactivestreams.utils.spi.Graph;
import org.reactivestreams.utils.spi.UnsupportedStageException;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;

public class ReactiveStreamsEngineImpl implements ReactiveStreamsEngine {
  private final MutexExecutor executor = new MutexExecutor(ForkJoinPool.commonPool());

  @Override
  public <T> Flow.Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
    return GraphLogic.buildPublisher(executor, graph);
  }

  @Override
  public <T, R> SubscriberWithResult<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
    return GraphLogic.buildSubscriber(executor, graph);
  }

  @Override
  public <T, R> Flow.Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
    return GraphLogic.buildProcessor(executor, graph);
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
    return GraphLogic.buildCompletion(executor, graph);
  }
}
