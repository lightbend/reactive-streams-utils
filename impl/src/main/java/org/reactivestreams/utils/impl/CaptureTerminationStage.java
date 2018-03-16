package org.reactivestreams.utils.impl;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public class CaptureTerminationStage<T> extends GraphStage implements GraphLogic.InletListener, GraphLogic.OutletListener {
  private final GraphLogic.StageInlet<T> inlet;
  private final GraphLogic.StageOutlet<T> outlet;
  private final CompletableFuture<Void> result;

  public CaptureTerminationStage(GraphLogic graphLogic, GraphLogic.StageInlet<T> inlet, GraphLogic.StageOutlet<T> outlet, CompletableFuture<Void> result) {
    super(graphLogic);
    this.inlet = inlet;
    this.outlet = outlet;
    this.result = result;

    inlet.setListener(this);
    outlet.setListener(this);
  }

  @Override
  public void onPush() {
    outlet.push(inlet.grab());
  }

  @Override
  public void onUpstreamFinish() {
    outlet.finish();
    result.complete(null);
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    outlet.fail(error);
    result.completeExceptionally(error);
  }

  @Override
  public void onPull() {
    inlet.pull();
  }

  @Override
  public void onDownstreamFinish() {
    inlet.finish();
    result.completeExceptionally(new CancellationException("Cancelled"));
  }
}
