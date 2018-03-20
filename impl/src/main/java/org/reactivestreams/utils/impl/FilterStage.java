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

import java.util.function.Predicate;

/**
 * A filter stage.
 */
class FilterStage<T> extends GraphStage implements InletListener, OutletListener {
  private final StageInlet<T> inlet;
  private final StageOutlet<T> outlet;
  private final Predicate<T> predicate;

  FilterStage(BuiltGraph builtGraph, StageInlet<T> inlet, StageOutlet<T> outlet, Predicate<T> predicate) {
    super(builtGraph);
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
    outlet.complete();
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
    inlet.cancel();
  }
}
