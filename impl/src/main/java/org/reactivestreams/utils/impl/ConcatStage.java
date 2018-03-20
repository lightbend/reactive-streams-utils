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

public class ConcatStage<T> extends GraphStage implements OutletListener {

  private final StageInlet<T> first;
  private final StageInlet<T> second;
  private final StageOutlet<T> outlet;

  private Throwable secondError;
  private boolean backpressureless;

  public ConcatStage(BuiltGraph builtGraph, StageInlet<T> first, StageInlet<T> second, StageOutlet<T> outlet) {
    super(builtGraph);
    this.first = first;
    this.second = second;
    this.outlet = outlet;

    first.setListener(new FirstInletListener());
    second.setListener(new SecondInletListener());
    outlet.setListener(this);
  }

  @Override
  public void onPull() {
    if (first.isClosed()) {
      second.pull();
    } else {
      first.pull();
    }
  }

  @Override
  public void onBackpressurelessPull() {
    backpressureless = true;
    if (first.isClosed()) {
      second.backpressurelessPull();
    } else {
      first.backpressurelessPull();
    }
  }

  @Override
  public void onDownstreamFinish() {
    if (!first.isClosed()) {
      first.cancel();
    }
    if (!second.isClosed()) {
      second.cancel();
    }
  }

  private class FirstInletListener implements InletListener<T> {
    @Override
    public void onPush() {
      outlet.push(first.grab());
    }

    @Override
    public void onBackpressurelessPush(T element) {
      outlet.backpressurelessPush(element);
    }

    @Override
    public void onUpstreamFinish() {
      if (second.isClosed()) {
        if (secondError != null) {
          outlet.fail(secondError);
        } else {
          outlet.complete();
        }
      } else if (outlet.isAvailable()) {
        if (backpressureless) {
          second.backpressurelessPull();
        } else {
          second.pull();
        }
      }
    }

    @Override
    public void onUpstreamFailure(Throwable error) {
      outlet.fail(error);
      if (!second.isClosed()) {
        second.cancel();
      }
    }
  }

  private class SecondInletListener implements InletListener<T> {
    @Override
    public void onPush() {
      outlet.push(second.grab());
    }

    @Override
    public void onBackpressurelessPush(T element) {
      outlet.backpressurelessPush(element);
    }

    @Override
    public void onUpstreamFinish() {
      if (first.isClosed()) {
        outlet.complete();
      }
    }

    @Override
    public void onUpstreamFailure(Throwable error) {
      if (first.isClosed()) {
        outlet.fail(error);
      } else {
        secondError = error;
      }
    }
  }
}
