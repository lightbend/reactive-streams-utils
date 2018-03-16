package org.reactivestreams.utils.impl;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;

class CollectStage<T, A, R> extends GraphStage implements GraphLogic.InletListener {
  private final GraphLogic.StageInlet<T> inlet;
  private final CompletableFuture<R> result;
  private final Collector<T, A, R> collector;
  private A container;

  public CollectStage(GraphLogic graphLogic, GraphLogic.StageInlet<T> inlet,
      CompletableFuture<R> result, Collector<T, A, R> collector) {
    super(graphLogic);
    this.inlet = inlet;
    this.result = result;
    this.collector = collector;

    container = collector.supplier().get();
    inlet.setListener(this);
  }

  @Override
  protected void postStart() {
    // It's possible that an earlier stage finished immediately, so check first
    if (!inlet.isFinished()) {
      inlet.pull();
    }
  }

  @Override
  public void onPush() {
    collector.accumulator().accept(container, inlet.grab());
    inlet.pull();
  }

  @Override
  public void onUpstreamFinish() {
    result.complete(collector.finisher().apply(container));
    container = null;
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    result.completeExceptionally(error);
    container = null;
  }
}
