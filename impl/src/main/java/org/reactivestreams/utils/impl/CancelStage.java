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

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A cancel stage.
 */
class CancelStage<T> extends GraphStage implements InletListener<T> {
  private final StageInlet<?> inlet;
  private final CompletableFuture<Void> result;

  CancelStage(BuiltGraph builtGraph, StageInlet<T> inlet, CompletableFuture<Void> result) {
    super(builtGraph);
    this.inlet = inlet;
    this.result = result;

    inlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!inlet.isClosed()) {
      inlet.cancel();
    }
    result.complete(null);
  }

  @Override
  public void onPush() {
  }

  @Override
  public void onUpstreamFinish() {
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
  }
}
