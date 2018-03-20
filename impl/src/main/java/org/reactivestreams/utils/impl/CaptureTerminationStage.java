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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * Stage that just captures termination signals, and redeems the given completable future when it does.
 */
public class CaptureTerminationStage<T> extends GraphStage implements InletListener, OutletListener {
  private final StageInlet<T> inlet;
  private final StageOutlet<T> outlet;
  private final CompletableFuture<Void> result;

  public CaptureTerminationStage(BuiltGraph builtGraph, StageInlet<T> inlet, StageOutlet<T> outlet, CompletableFuture<Void> result) {
    super(builtGraph);
    this.inlet = inlet;
    this.outlet = outlet;
    this.result = result;

    inlet.setListener(this);
    outlet.setListener(this);
  }

  @Override
  public void onPush() {
    outlet.push(inlet.grab());
  }

  @Override
  public void onUpstreamFinish() {
    outlet.complete();
    result.complete(null);
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    outlet.fail(error);
    result.completeExceptionally(error);
  }

  @Override
  public void onPull() {
    inlet.pull();
  }

  @Override
  public void onDownstreamFinish() {
    inlet.cancel();
    result.completeExceptionally(new CancellationException("Cancelled"));
  }
}
