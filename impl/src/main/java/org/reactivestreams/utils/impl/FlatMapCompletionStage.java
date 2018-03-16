package org.reactivestreams.utils.impl;

import org.reactivestreams.utils.spi.Graph;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

class FlatMapCompletionStage<T, R> extends GraphStage implements GraphLogic.InletListener, GraphLogic.OutletListener {
  private final GraphLogic.StageInlet<T> inlet;
  private final GraphLogic.StageOutlet<R> outlet;
  private final Function<T, CompletionStage<R>> mapper;

  private Throwable error;

  FlatMapCompletionStage(GraphLogic graphLogic, GraphLogic.StageInlet<T> inlet, GraphLogic.StageOutlet<R> outlet, Function<T, CompletionStage<R>> mapper) {
    super(graphLogic);
    this.inlet = inlet;
    this.outlet = outlet;
    this.mapper = mapper;

    inlet.setListener(this);
    outlet.setListener(this);
  }

  @Override
  public void onPush() {
    CompletionStage<R> future = mapper.apply(inlet.grab());
    future.whenCompleteAsync((result, error) -> {
      if (!outlet.isFinished()) {
        if (error == null) {
          outlet.push(result);
          if (inlet.isFinished()) {
            if (this.error != null) {
              outlet.fail(this.error);
            } else {
              outlet.finish();
            }
          }
        } else {

          outlet.fail(error);
          if (!inlet.isFinished()) {
            inlet.finish();
          }
        }
      }
    }, executor());
  }

  @Override
  public void onUpstreamFinish() {
    if (!activeCompletionStage()) {
      outlet.finish();
    }
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    if (activeCompletionStage()) {
      this.error = error;
    } else {
      outlet.fail(error);
    }
  }

  private boolean activeCompletionStage() {
    return outlet.isAvailable() && !inlet.isPulled();
  }

  @Override
  public void onPull() {
    inlet.pull();
  }

  @Override
  public void onDownstreamFinish() {
    inlet.finish();
  }
}
