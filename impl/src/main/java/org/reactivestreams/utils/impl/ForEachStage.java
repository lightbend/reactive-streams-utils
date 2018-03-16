package org.reactivestreams.utils.impl;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

class ForEachStage<T> extends GraphStage implements GraphLogic.InletListener {
  private final GraphLogic.StageInlet<T> inlet;
  private final CompletableFuture<Void> result;
  private final Consumer<? super T> action;

  ForEachStage(GraphLogic graphLogic, GraphLogic.StageInlet<T> inlet, CompletableFuture<Void> result, Consumer<? super T> action) {
    super(graphLogic);
    this.inlet = inlet;
    this.result = result;
    this.action = action;

    inlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!inlet.isFinished()) {
      inlet.pull();
    }
  }

  @Override
  public void onPush() {
    action.accept(inlet.grab());
    inlet.pull();
  }

  @Override
  public void onUpstreamFinish() {
    result.complete(null);
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    result.completeExceptionally(error);
  }
}
