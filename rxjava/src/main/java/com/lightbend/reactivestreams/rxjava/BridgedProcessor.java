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

package com.lightbend.reactivestreams.rxjava;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

/**
 * I may have missed something, but I can't for the life of me see anything in rxjava that allows creating a
 * Processor or Subscriber where backpressure from the target Subscriber is propagated to eventual Publisher.
 * Things like UnicastProcessor have an unbounded buffer, PublisherProcessor drops elements until a Subscriber
 * connects, etc.
 *
 * So, this processor simply waits until it gets both a publish, and an onSubscribe, and then connects the two,
 * ensuring a 1:1 connection with backpressure propagation. This allows it be wrapped as a publisher by RxJava.
 */
class BridgedProcessor<T> implements Flow.Processor<T, T> {

  private enum State {
    INACTIVE, HAS_SUBSCRIBER, HAS_SUBSCRIPTION, RUNNING, HAS_ERROR, COMPLETE
  }
  private final AtomicReference<Flow.Subscriber<? super T>> subscriber = new AtomicReference<>();
  private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
  private final AtomicReference<State> state = new AtomicReference<>(State.INACTIVE);
  private volatile Throwable throwable;

  @Override
  public void subscribe(Flow.Subscriber<? super T> subscriber) {
    Objects.requireNonNull(subscriber, "Subscriber must not be null");
    if (!this.subscriber.compareAndSet(null, subscriber)) {
      subscriber.onSubscribe(new NullSubsription());
      subscriber.onError(new IllegalStateException("BridgedPublisher only supports one subscriber."));
    } else {
      switch (state.compareAndExchange(State.INACTIVE, State.HAS_SUBSCRIBER)) {
        case INACTIVE:
          break;

        case HAS_SUBSCRIPTION:
          subscriber.onSubscribe(new WrappedSubscription(subscription.get()));
          switch (state.compareAndExchange(State.HAS_SUBSCRIPTION, State.RUNNING)) {
            case HAS_SUBSCRIPTION:
              break;
            // In the time that we've subscribed, we may have received an error or complete
            // signal
            case HAS_ERROR:
              subscriber.onError(throwable);
              break;
            case COMPLETE:
              subscriber.onComplete();
              break;
          }
          break;

        case HAS_ERROR:
          subscriber.onSubscribe(new NullSubsription());
          subscriber.onError(throwable);
          break;

        case COMPLETE:
          subscriber.onSubscribe(new NullSubsription());
          subscriber.onComplete();
          break;

      }
    }
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    Objects.requireNonNull(subscription, "Subscription must not be null");
    if (!this.subscription.compareAndSet(null, subscription)) {
      subscription.cancel();
    } else {
      switch (state.compareAndExchange(State.INACTIVE, State.HAS_SUBSCRIPTION)) {
        case INACTIVE:
          break;
        case HAS_SUBSCRIBER:
          state.set(State.RUNNING);
          subscriber.get().onSubscribe(new WrappedSubscription(subscription));
          break;
      }
    }
  }

  @Override
  public void onNext(T item) {
    // This shouldn't need to be done because the subscriber should do it itself, but rxjavas subscribers are not TCK
    // compliant, so we need to do this here.
    Objects.requireNonNull(item, "Item passed to onNext must not be null");
    Flow.Subscriber<? super T> subscriber = this.subscriber.get();
    if (subscriber == null) {
      throw new IllegalStateException("onNext invoked without demand present.");
    } else {
      subscriber.onNext(item);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    Objects.requireNonNull(throwable, "Throwable passed to onError must not be null");
    // We can't just go straight to the subscriber, because we can't be sure that onSubscribe has been invoked yet
    this.throwable = throwable;
    switch (state.compareAndExchange(State.HAS_SUBSCRIPTION, State.HAS_ERROR)) {
      case RUNNING:
        subscriber.get().onError(throwable);
        break;
      case HAS_SUBSCRIPTION:
        break;
      case HAS_ERROR:
      case COMPLETE:
        throw new IllegalStateException("onError invoked after completion.");
      case INACTIVE:
        throw new IllegalStateException("onError invoked before onSubscribe.");
    }
  }

  @Override
  public void onComplete() {
    // We can't just go straight to the subscriber, because we can't be sure that onSubscribe has been invoked yet
    switch (state.compareAndExchange(State.HAS_SUBSCRIPTION, State.COMPLETE)) {
      case RUNNING:
        subscriber.get().onComplete();
        break;
      case HAS_SUBSCRIPTION:
        break;
      case HAS_ERROR:
      case COMPLETE:
        throw new IllegalStateException("onComplete invoked after completion.");
      case INACTIVE:
        throw new IllegalStateException("onComplete invoked before onSubscribe.");
    }
  }

  private static class NullSubsription implements Flow.Subscription {
    @Override
    public void request(long n) { }

    @Override
    public void cancel() { }
  }

  /**
   * Subscriber that just cancels.
   *
   * We use this to replace our reference to the original subscriber when it cancels.
   */
  private static class CancelledSubscriber<T> implements Flow.Subscriber<T> {
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
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
  }

  /**
   * Ensures the reference to the subscriber is dropped when cancelled.
   */
  private class WrappedSubscription implements Flow.Subscription {
    private final Flow.Subscription delegate;

    public WrappedSubscription(Flow.Subscription delegate) {
      this.delegate = delegate;
    }

    @Override
    public void request(long n) {
      delegate.request(n);
    }

    @Override
    public void cancel() {
      subscriber.set(new CancelledSubscriber<>());
      delegate.cancel();
    }
  }
}
