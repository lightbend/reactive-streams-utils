package org.reactivestreams.utils.impl;

import java.util.Iterator;

class OfStage<T> extends GraphStage implements GraphLogic.OutletListener {
  private final GraphLogic.StageOutlet<T> outlet;
  private Iterator<T> elements;

  public OfStage(GraphLogic graphLogic, GraphLogic.StageOutlet<T> outlet, Iterable<T> elements) {
    super(graphLogic);
    this.outlet = outlet;
    this.elements = elements.iterator();

    outlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!outlet.isFinished()) {
      if (!elements.hasNext()) {
        outlet.finish();
      }
    }
  }

  @Override
  public void onPull() {
    outlet.push(elements.next());
    if (!elements.hasNext() && !outlet.isFinished()) {
      outlet.finish();
    }
  }

  @Override
  public void onDownstreamFinish() {
  }
}
