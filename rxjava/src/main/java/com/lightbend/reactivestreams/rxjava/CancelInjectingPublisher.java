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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Injects cancellation into a publisher if it hasn't been subscribed to before cancelIfNotSubscribed is invoked.
 */
public class CancelInjectingPublisher<T> implements Publisher<T> {
  private final Publisher<T> delegate;
  private final AtomicBoolean subscribed = new AtomicBoolean();

  public CancelInjectingPublisher(Publisher<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    Objects.requireNonNull(subscriber);
    if (subscribed.compareAndSet(false, true)) {
      delegate.subscribe(subscriber);
    } else {
      subscriber.onSubscribe(new Subscription() {
        @Override
        public void request(long n) { }
        @Override
        public void cancel() { }
      });
      subscriber.onError(new IllegalStateException("CancelInjectingPublisher only supports one subscriber"));
    }
  }

  public void cancelIfNotSubscribed() {
    if (subscribed.compareAndSet(false, true)) {
      delegate.subscribe(new Subscriber<T>() {
        @Override
        public void onSubscribe(Subscription subscription) {
          Objects.requireNonNull(subscription);
          subscription.cancel();
        }
        @Override
        public void onNext(T item) {
          Objects.requireNonNull(item);
        }

        @Override
        public void onError(Throwable throwable) {
          Objects.requireNonNull(throwable);
        }

        @Override
        public void onComplete() { }
      });
    }
  }
}
