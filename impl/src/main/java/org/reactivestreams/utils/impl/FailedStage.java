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

/**
 * A failed stage. Does nothing but fails the stream when the graph starts.
 */
class FailedStage extends GraphStage implements OutletListener {
  private final Throwable error;
  private final StageOutlet<?> outlet;

  public FailedStage(BuiltGraph builtGraph, StageOutlet<?> outlet, Throwable error) {
    super(builtGraph);
    this.outlet = outlet;
    this.error = error;

    outlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!outlet.isClosed()) {
      outlet.fail(error);
    }
  }

  @Override
  public void onPull() {
  }

  @Override
  public void onDownstreamFinish() {
  }
}
