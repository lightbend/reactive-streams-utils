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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

class FindFirstStage<T> extends GraphStage implements InletListener {

  private final StageInlet<T> inlet;
  private final CompletableFuture<Optional<T>> result;

  FindFirstStage(BuiltGraph builtGraph, StageInlet<T> inlet, CompletableFuture<Optional<T>> result) {
    super(builtGraph);
    this.inlet = inlet;
    this.result = result;

    inlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!inlet.isClosed()) {
      inlet.pull();
    }
  }

  @Override
  public void onPush() {
    result.complete(Optional.of(inlet.grab()));
    inlet.cancel();
  }

  @Override
  public void onUpstreamFinish() {
    result.complete(Optional.empty());
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    result.completeExceptionally(error);
  }
}
