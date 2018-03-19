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
 * An inlet that a stage may interact with.
 *
 * @param <T> The type of signal this stage deals with.
 */
interface StageInlet<T> {

  /**
   * Send a pull signal to this inlet. This will allow an upstream stage to push an element.
   * <p>
   * The inlet may only be pulled if it is not closed and hasn't already been pulled since it last received an element.
   */
  void pull();

  /**
   * Whether this inlet has been pulled.
   */
  boolean isPulled();

  /**
   * Whether this inlet is available to be grabbed.
   */
  boolean isAvailable();

  /**
   * Whether this inlet has been closed, either due to it being explicitly cancelled, or due to an
   * upstream finish or failure being received.
   */
  boolean isClosed();

  /**
   * Cancel this inlet. No signals may be sent after this is invoked, and no signals will be received.
   */
  void cancel();

  /**
   * Grab the last pushed element from this inlet.
   * <p>
   * Grabbing the element will cause it to be removed from the inlet - an element cannot be grabbed twice.
   * <p>
   * This may only be invoked if a prior {@link InletListener#onPush()} signal has been received.
   *
   * @return The grabbed element.
   */
  T grab();

  /**
   * Set the listener for signals from this inlet.
   *
   * @param listener The listener.
   */
  void setListener(InletListener listener);
}

/**
 * A listener for signals to an inlet.
 */
interface InletListener {

  /**
   * Indicates that an element has been pushed. The element can be received using {@link StageInlet#grab()}.
   */
  void onPush();

  /**
   * Indicates that upstream has completed the stream. No signals may be sent to the inlet after this has been invoked.
   */
  void onUpstreamFinish();

  /**
   * Indicates that upstream has completed the stream with a failure. No signals may be sent to the inlet after this has
   * been invoked.
   */
  void onUpstreamFailure(Throwable error);
}
