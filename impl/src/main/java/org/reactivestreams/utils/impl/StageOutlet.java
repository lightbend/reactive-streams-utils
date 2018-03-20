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
 * An outlet that a stage may interact with.
 *
 * @param <T> The type of elements that this outlet supports.
 */
interface StageOutlet<T> {

  /**
   * Push an element.
   * <p>
   * An element may only be pushed if an {@link OutletListener#onPull()} signal has been received, and the outlet
   * hasn't been completed, failed or a {@link OutletListener#onDownstreamFinish()} hasn't been received.
   *
   * @param element The element to push.
   */
  void push(T element);

  /**
   * Push an element without backpressure.
   * <p>
   * Elements may only be pushed via this method if {@link OutletListener#onBackpressurelessPull()} has been invoked.
   * If this method is invoked, the stage can expect not to receive a subsequent {@link OutletListener#onPull()} signal
   * to pull more elements, instead, it should continue invoking this method as long as it has elements to push.
   */
  void backpressurelessPush(T element);

  /**
   * Whether this outlet is available for an element to be pushed.
   */
  boolean isAvailable();

  /**
   * Complete this outlet.
   */
  void complete();

  /**
   * Whether this outlet is closed, either due to sending a complete or fail signal, or due to downstream
   * completing by invoking {@link OutletListener#onDownstreamFinish()}.
   */
  boolean isClosed();

  /**
   * Fail this outlet.
   *
   * @param error The error to fail it with.
   */
  void fail(Throwable error);

  /**
   * Set the listener for signals from this outlet.
   *
   * @param listener The listener to set.
   */
  void setListener(OutletListener listener);
}

/**
 * An listener to receive signals from an outlet.
 */
interface OutletListener {
  /**
   * A pull signal, indicates that downstream is ready to be pushed to.
   */
  void onPull();

  /**
   * A backpressureless pull signal, indicates that downstream can be pushed to using
   * {@link StageOutlet#backpressurelessPush(Object)} without receiving any further {@link #onPull()} signals.
   *
   * If a stage receives this signal, it may optionally use the {@link StageOutlet#backpressurelessPush(Object)}
   * method to push as many times as it wants without receiving onPull signals, or it may use the regular
   * {@link StageOutlet#push(Object)} to push with backpressure, waiting to receive onPull signals between each one.
   */
  default void onBackpressurelessPull() {
    onPull();
  }

  /**
   * A completion signal, indicates that downstream has completed. No further signals may be sent to this outlet after
   * this signal is received.
   */
  void onDownstreamFinish();
}
