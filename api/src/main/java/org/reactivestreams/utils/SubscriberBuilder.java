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

import org.reactivestreams.utils.spi.Stage;

import java.util.concurrent.Flow.*;

/**
 * A builder for a {@link Subscriber} and its result.
 * <p>
 * When built, this builder returns a {@link SubscriberWithResult}, which encapsulates both a {@link Subscriber} and a
 * {@link java.util.concurrent.CompletionStage} that will be redeemed with the result produced by the subscriber when
 * the stream completes normally, or will be redeemed with an error if the subscriber receives an error.
 *
 * @param <T> The type of the elements that this subscriber consumes.
 * @param <R> The type of the result that this subscriber emits.
 * @see ReactiveStreams
 */
public final class SubscriberBuilder<T, R> extends ReactiveStreamsBuilder<SubscriberWithResult<T, R>> {

  SubscriberBuilder(Stage stage, ReactiveStreamsBuilder<?> previous) {
    super(stage, previous);
  }

  @Override
  public SubscriberWithResult<T, R> build(ReactiveStreamsEngine engine) {
    return engine.buildSubscriber(verify(flatten(), true, false));
  }
}
