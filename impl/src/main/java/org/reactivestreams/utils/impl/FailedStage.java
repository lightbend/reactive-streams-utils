package org.reactivestreams.utils.impl;

class FailedStage extends GraphStage implements GraphLogic.OutletListener {
  private final Throwable error;
  private final GraphLogic.StageOutlet<?> outlet;

  public FailedStage(GraphLogic graphLogic, GraphLogic.StageOutlet<?> outlet, Throwable error) {
    super(graphLogic);
    this.outlet = outlet;
    this.error = error;

    outlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!outlet.isFinished()) {
      outlet.fail(error);
    }
  }

  @Override
  public void onPull() {
  }

  @Override
  public void onDownstreamFinish() {
  }
}
