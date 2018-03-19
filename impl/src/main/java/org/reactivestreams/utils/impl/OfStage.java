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

import java.util.Iterator;

/**
 * Of stage.
 */
class OfStage<T> extends GraphStage implements OutletListener {
  private final StageOutlet<T> outlet;
  private Iterator<T> elements;

  public OfStage(BuiltGraph builtGraph, StageOutlet<T> outlet, Iterable<T> elements) {
    super(builtGraph);
    this.outlet = outlet;
    this.elements = elements.iterator();

    outlet.setListener(this);
  }

  @Override
  protected void postStart() {
    if (!outlet.isClosed()) {
      if (!elements.hasNext()) {
        outlet.complete();
      }
    }
  }

  @Override
  public void onPull() {
    outlet.push(elements.next());
    if (!elements.hasNext() && !outlet.isClosed()) {
      outlet.complete();
    }
  }

  @Override
  public void onDownstreamFinish() {
  }
}
