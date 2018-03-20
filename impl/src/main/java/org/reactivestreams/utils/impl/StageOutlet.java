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
   * A completion signal, indicates that downstream has completed. No further signals may be sent to this outlet after
   * this signal is received.
   */
  void onDownstreamFinish();
}
