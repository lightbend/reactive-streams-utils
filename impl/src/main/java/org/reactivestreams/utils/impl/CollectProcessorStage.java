/******************************************************************************
 * Licensed under Public Domain (CC0)                                         *
 *                                                                            *
 * To the extent possible under law, the person who associated CC0 with       *
 * this code has waived all copyright and related or neighboring              *
 * rights to this code.                                                       *
 *                                                                            *
 * You should have received a copy of the CC0 legalcode along with this       *
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.     *
 ******************************************************************************/

package org.reactivestreams.utils.impl;

import java.util.stream.Collector;

/**
 * Stage that collects elements into a collector.
 */
class CollectProcessorStage<T, A, R> extends GraphStage implements InletListener, OutletListener {
  private final StageInlet<T> inlet;
  private final StageOutlet<R> outlet;
  private final Collector<T, A, R> collector;
  private A container;

  public CollectProcessorStage(BuiltGraph builtGraph, StageInlet<T> inlet,
      StageOutlet<R> outlet, Collector<T, A, R> collector) {
    super(builtGraph);
    this.inlet = inlet;
    this.outlet = outlet;
    this.collector = collector;

    container = collector.supplier().get();
    inlet.setListener(this);
    outlet.setListener(this);
  }

  @Override
  public void onPull() {
    if (inlet.isClosed()) {
      finish();
    } else {
      inlet.pull();
    }
  }

  @Override
  public void onDownstreamFinish() {
    inlet.cancel();
    container = null;
  }

  @Override
  public void onPush() {
    collector.accumulator().accept(container, inlet.grab());
    inlet.pull();
  }

  @Override
  public void onUpstreamFinish() {
    // If the stream is empty, then it's possible that outlet hasn't pulled yet.
    if (outlet.isAvailable()) {
      finish();
    }
  }

  private final void finish() {
    outlet.push(collector.finisher().apply(container));
    outlet.complete();
    container = null;
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    outlet.fail(error);
    container = null;
  }
}
