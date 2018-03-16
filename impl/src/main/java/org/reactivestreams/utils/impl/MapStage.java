package org.reactivestreams.utils.impl;

import java.util.function.Function;

class MapStage<T, R> extends GraphStage implements GraphLogic.InletListener, GraphLogic.OutletListener {
  private final GraphLogic.StageInlet<T> inlet;
  private final GraphLogic.StageOutlet<R> outlet;
  private final Function<T, R> mapper;

  MapStage(GraphLogic graphLogic, GraphLogic.StageInlet<T> inlet, GraphLogic.StageOutlet<R> outlet, Function<T, R> mapper) {
    super(graphLogic);
    this.inlet = inlet;
    this.outlet = outlet;
    this.mapper = mapper;

    inlet.setListener(this);
    outlet.setListener(this);
  }

  @Override
  public void onPush() {
    outlet.push(mapper.apply(inlet.grab()));
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
