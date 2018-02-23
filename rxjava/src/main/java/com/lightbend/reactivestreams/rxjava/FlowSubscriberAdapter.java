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

package com.lightbend.reactivestreams.rxjava;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * For some reason this doesn't exist (or at least, isn't made public) in RxJava.
 */
class FlowSubscriberAdapter<T> implements Subscriber<T> {
  private final Flow.Subscriber<T> delegate;

  FlowSubscriberAdapter(Flow.Subscriber<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    Objects.requireNonNull(subscription, "Subscription must not be null");
    delegate.onSubscribe(new Flow.Subscription() {
      @Override
      public void request(long n) {
        subscription.request(n);
      }
      @Override
      public void cancel() {
        subscription.cancel();
      }
    });
  }

  @Override
  public void onNext(T t) {
    delegate.onNext(t);
  }

  @Override
  public void onError(Throwable t) {
    delegate.onError(t);
  }

  @Override
  public void onComplete() {
    delegate.onComplete();
  }
}
