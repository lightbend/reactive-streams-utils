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
import java.util.stream.Collector;

/**
 * Stage that collects elements into a collector.
 */
class CollectStage<T, A, R> extends GraphStage implements InletListener<T> {
  private final StageInlet<T> inlet;
  private final CompletableFuture<R> result;
  private final Collector<T, A, R> collector;
  private A container;

  public CollectStage(BuiltGraph builtGraph, StageInlet<T> inlet,
      CompletableFuture<R> result, Collector<T, A, R> collector) {
    super(builtGraph);
    this.inlet = inlet;
    this.result = result;
    this.collector = collector;

    container = collector.supplier().get();
    inlet.setListener(this);
  }

  @Override
  protected void postStart() {
    // It's possible that an earlier stage finished immediately, so check first
    if (!inlet.isClosed()) {
      inlet.backpressurelessPull();
    }
  }

  @Override
  public void onPush() {
    // This shouldn't be executed because we've done a backpressureless pull, but anyway.
    collector.accumulator().accept(container, inlet.grab());
    inlet.pull();
  }

  @Override
  public void onBackpressurelessPush(T element) {
    collector.accumulator().accept(container, element);
  }

  @Override
  public void onUpstreamFinish() {
    result.complete(collector.finisher().apply(container));
    container = null;
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    result.completeExceptionally(error);
    container = null;
  }
}
