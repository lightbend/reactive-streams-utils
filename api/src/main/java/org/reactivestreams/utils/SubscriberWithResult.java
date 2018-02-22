/************************************************************************
 * Licensed under Public Domain (CC0)                                    *
 *                                                                       *
 * To the extent possible under law, the person who associated CC0 with  *
 * this code has waived all copyright and related or neighboring         *
 * rights to this code.                                                  *
 *                                                                       *
 * You should have received a copy of the CC0 legalcode along with this  *
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.*
 ************************************************************************/

package org.reactivestreams.utils;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.*;

/**
 * A subscriber with a result.
 * <p>
 * The result is provided through a {@link CompletionStage}, which is redeemed when the subscriber receives a
 * completion or error signal, or otherwise cancels the stream.
 *
 * @param <T> The type of the elements that the subscriber consumes.
 * @param <R> The type of the result that the subscriber emits.
 */
public final class SubscriberWithResult<T, R> {
  private final Subscriber<T> subscriber;
  private final CompletionStage<R> result;

  public SubscriberWithResult(Subscriber<T> subscriber, CompletionStage<R> result) {
    this.subscriber = subscriber;
    this.result = result;
  }

  /**
   * Get the subscriber.
   *
   * @return The subscriber.
   */
  public Subscriber<T> getSubscriber() {
    return subscriber;
  }

  /**
   * Get the result.
   *
   * @return The result.
   */
  public CompletionStage<R> getResult() {
    return result;
  }
}
