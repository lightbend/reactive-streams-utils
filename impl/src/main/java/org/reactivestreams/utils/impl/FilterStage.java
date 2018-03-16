package org.reactivestreams.utils.impl;

import java.util.function.Predicate;

class FilterStage<T> extends GraphStage implements GraphLogic.InletListener, GraphLogic.OutletListener {
  private final GraphLogic.StageInlet<T> inlet;
  private final GraphLogic.StageOutlet<T> outlet;
  private final Predicate<T> predicate;

  FilterStage(GraphLogic graphLogic, GraphLogic.StageInlet<T> inlet, GraphLogic.StageOutlet<T> outlet, Predicate<T> predicate) {
    super(graphLogic);
    this.inlet = inlet;
    this.outlet = outlet;
    this.predicate = predicate;

    inlet.setListener(this);
    outlet.setListener(this);
  }

  @Override
  public void onPush() {
    T element = inlet.grab();
    if (predicate.test(element)) {
      outlet.push(element);
    } else {
      inlet.pull();
    }
  }

  @Override
  public void onUpstreamFinish() {
    outlet.finish();
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    outlet.fail(error);
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
