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

import java.util.concurrent.Flow;

/**
 * Processor that wraps a publisher and subscriber
 */
class WrappedProcessor<T, R> implements Flow.Processor<T, R> {
  private final Flow.Subscriber<T> subscriber;
  private final Flow.Publisher<R> publisher;

  public WrappedProcessor(Flow.Subscriber<T> subscriber, Flow.Publisher<R> publisher) {
    this.subscriber = subscriber;
    this.publisher = publisher;
  }

  @Override
  public void subscribe(Flow.Subscriber<? super R> subscriber) {
    publisher.subscribe(subscriber);
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    subscriber.onSubscribe(subscription);
  }

  @Override
  public void onNext(T item) {
    subscriber.onNext(item);
  }

  @Override
  public void onError(Throwable throwable) {
    subscriber.onError(throwable);
  }

  @Override
  public void onComplete() {
    subscriber.onComplete();
  }
}
