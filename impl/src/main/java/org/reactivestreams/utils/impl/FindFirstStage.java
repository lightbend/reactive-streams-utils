package org.reactivestreams.utils.impl;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

class FindFirstStage<T> extends GraphStage implements GraphLogic.InletListener {

  private final GraphLogic.StageInlet<T> inlet;
  private final CompletableFuture<Optional<T>> result;

  FindFirstStage(GraphLogic graphLogic, GraphLogic.StageInlet<T> inlet, CompletableFuture<Optional<T>> result) {
    super(graphLogic);
    this.inlet = inlet;
    this.result = result;

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
    result.complete(Optional.of(inlet.grab()));
    inlet.finish();
  }

  @Override
  public void onUpstreamFinish() {
    result.complete(Optional.empty());
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    result.completeExceptionally(error);
  }
}
