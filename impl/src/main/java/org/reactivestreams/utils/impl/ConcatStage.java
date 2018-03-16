package org.reactivestreams.utils.impl;

public class ConcatStage<T> extends GraphStage implements GraphLogic.OutletListener {

  private final GraphLogic.StageInlet<T> first;
  private final GraphLogic.StageInlet<T> second;
  private final GraphLogic.StageOutlet<T> outlet;

  private Throwable secondError;

  public ConcatStage(GraphLogic graphLogic, GraphLogic.StageInlet<T> first, GraphLogic.StageInlet<T> second, GraphLogic.StageOutlet<T> outlet) {
    super(graphLogic);
    this.first = first;
    this.second = second;
    this.outlet = outlet;

    first.setListener(new FirstInletListener());
    second.setListener(new SecondInletListener());
    outlet.setListener(this);
  }

  @Override
  public void onPull() {
    if (first.isFinished()) {
      second.pull();
    } else {
      first.pull();
    }
  }

  @Override
  public void onDownstreamFinish() {
    if (!first.isFinished()) {
      first.finish();
    }
    if (!second.isFinished()) {
      second.finish();
    }
  }

  private class FirstInletListener implements GraphLogic.InletListener {
    @Override
    public void onPush() {
      outlet.push(first.grab());
    }

    @Override
    public void onUpstreamFinish() {
      if (second.isFinished()) {
        if (secondError != null) {
          outlet.fail(secondError);
        } else {
          outlet.finish();
        }
      } else if (outlet.isAvailable()) {
        second.pull();
      }
    }

    @Override
    public void onUpstreamFailure(Throwable error) {
      outlet.fail(error);
      if (!second.isFinished()) {
        second.finish();
      }
    }
  }

  private class SecondInletListener implements GraphLogic.InletListener {
    @Override
    public void onPush() {
      outlet.push(second.grab());
    }

    @Override
    public void onUpstreamFinish() {
      if (first.isFinished()) {
        outlet.finish();
      }
    }

    @Override
    public void onUpstreamFailure(Throwable error) {
      if (first.isFinished()) {
        outlet.fail(error);
      } else {
        secondError = error;
      }
    }
  }
}
