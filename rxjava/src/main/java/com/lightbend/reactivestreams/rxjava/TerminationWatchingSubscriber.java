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

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

class TerminationWatchingSubscriber<T> implements Flow.Subscriber<T> {

  private final CompletableFuture<Void> termination = new CompletableFuture<>();
  private final Flow.Subscriber<T> delegate;

  TerminationWatchingSubscriber(Flow.Subscriber<T> delegate) {
    this.delegate = delegate;
  }

  CompletionStage<Void> getTermination() {
    return termination;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    Objects.requireNonNull(subscription, "Subscription must not be null");
    delegate.onSubscribe(new Subscription(subscription));
  }

  @Override
  public void onNext(T item) {
    delegate.onNext(item);
  }

  @Override
  public void onError(Throwable throwable) {
    termination.completeExceptionally(throwable);
    delegate.onError(throwable);
  }

  @Override
  public void onComplete() {
    termination.complete(null);
    delegate.onComplete();
  }

  private class Subscription implements Flow.Subscription {
    private final Flow.Subscription delegate;

    public Subscription(Flow.Subscription delegate) {
      this.delegate = delegate;
    }

    @Override
    public void request(long n) {
      delegate.request(n);
    }

    @Override
    public void cancel() {
      termination.completeExceptionally(new CancellationException());
      delegate.cancel();

    }
  }
}
