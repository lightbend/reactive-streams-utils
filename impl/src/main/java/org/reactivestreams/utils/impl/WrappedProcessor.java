package org.reactivestreams.utils.impl;

import java.util.concurrent.Flow;

public class WrappedProcessor<T, R> implements Flow.Processor<T, R> {
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

