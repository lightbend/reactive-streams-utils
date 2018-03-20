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
 * Take while stage.
 */
class TakeWhileStage<T> extends GraphStage implements InletListener<T>, OutletListener {
  private final StageInlet<T> inlet;
  private final StageOutlet<T> outlet;
  private final Predicate<T> predicate;
  private final boolean inclusive;

  TakeWhileStage(BuiltGraph builtGraph, StageInlet<T> inlet, StageOutlet<T> outlet, Predicate<T> predicate, boolean inclusive) {
    super(builtGraph);
    this.inlet = inlet;
    this.outlet = outlet;
    this.predicate = predicate;
    this.inclusive = inclusive;

    inlet.setListener(this);
    outlet.setListener(this);
  }

  @Override
  public void onPush() {
    T element = inlet.grab();
    if (predicate.test(element)) {
      outlet.push(element);
    } else {
      if (inclusive) {
        outlet.push(element);
      }
      outlet.complete();
      inlet.cancel();
    }
  }

  @Override
  public void onBackpressurelessPush(T element) {
    if (predicate.test(element)) {
      outlet.backpressurelessPush(element);
    } else {
      if (inclusive) {
        outlet.backpressurelessPush(element);
      }
      outlet.complete();
      inlet.cancel();
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
  public void onBackpressurelessPull() {
    inlet.backpressurelessPull();
  }

  @Override
  public void onDownstreamFinish() {
    inlet.cancel();
  }
}
