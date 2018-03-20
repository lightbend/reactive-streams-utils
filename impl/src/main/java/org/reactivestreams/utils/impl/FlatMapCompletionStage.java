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

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Flat maps to completion stages of elements.
 */
class FlatMapCompletionStage<T, R> extends GraphStage implements InletListener, OutletListener {
  private final StageInlet<T> inlet;
  private final StageOutlet<R> outlet;
  private final Function<T, CompletionStage<R>> mapper;

  private Throwable error;

  FlatMapCompletionStage(BuiltGraph builtGraph, StageInlet<T> inlet, StageOutlet<R> outlet, Function<T, CompletionStage<R>> mapper) {
    super(builtGraph);
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
      if (!outlet.isClosed()) {
        if (error == null) {
          outlet.push(result);
          if (inlet.isClosed()) {
            if (this.error != null) {
              outlet.fail(this.error);
            } else {
              outlet.complete();
            }
          }
        } else {

          outlet.fail(error);
          if (!inlet.isClosed()) {
            inlet.cancel();
          }
        }
      }
    }, executor());
  }

  @Override
  public void onUpstreamFinish() {
    if (!activeCompletionStage()) {
      outlet.complete();
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
    inlet.cancel();
  }
}
