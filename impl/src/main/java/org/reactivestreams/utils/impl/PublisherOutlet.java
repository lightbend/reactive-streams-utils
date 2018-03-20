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

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * An outlet that is a publisher.
 *
 * This is either the last outlet for a graph that has an outlet, or is used to connect a Processor or Publisher stage
 * in a graph.
 */
final class PublisherOutlet<T> implements StageOutlet<T>, Flow.Publisher<T>, Flow.Subscription, Port, UnrolledSignal {

  private final BuiltGraph builtGraph;

  private Flow.Subscriber<? super T> subscriber;
  private boolean pulled;
  private long demand;
  private boolean finished;
  private Throwable failure;
  private OutletListener listener;

  PublisherOutlet(BuiltGraph builtGraph) {
    this.builtGraph = builtGraph;
  }

  private boolean isBackpressureless() {
    return demand == Long.MAX_VALUE;
  }

  @Override
  public void onStreamFailure(Throwable reason) {
    if (!finished) {
      finished = true;
      demand = 0;
      if (subscriber != null) {
        try {
          subscriber.onError(reason);
        } catch (Exception e) {
          // Ignore
        }
      } else {
        failure = reason;
      }
      listener.onDownstreamFinish();
    }
  }

  @Override
  public void verifyReady() {
    if (listener == null) {
      throw new IllegalStateException("Cannot start stream without inlet listener set");
    }
  }

  @Override
  public void subscribe(Flow.Subscriber<? super T> subscriber) {
    Objects.requireNonNull(subscriber, "Subscriber must not be null");
    builtGraph.execute(() -> {
      if (this.subscriber != null) {
        subscriber.onSubscribe(new Flow.Subscription() {
          @Override
          public void request(long n) {
          }

          @Override
          public void cancel() {
          }
        });
        subscriber.onError(new IllegalStateException("This publisher only supports one subscriber"));
      } else {
        this.subscriber = subscriber;
        subscriber.onSubscribe(this);
        if (finished) {
          if (failure != null) {
            subscriber.onError(failure);
            failure = null;
            this.subscriber = null;
          } else {
            subscriber.onComplete();
            this.subscriber = null;
          }
        }
      }
    });
  }

  @Override
  public void request(long n) {
    builtGraph.execute(() -> {
      if (!finished) {
        if (n <= 0) {
          onStreamFailure(new IllegalArgumentException("Request demand must be greater than zero"));
        } else {
          if (isBackpressureless()) {
            // Ignore
          } else {
            boolean existingDemand = demand > 0;
            demand = demand + n;
            if (demand <= 0) {
              demand = Long.MAX_VALUE;
            }
            if (!existingDemand) {
              doPull();
            }
          }
        }
      }
    });
  }

  @Override
  public void signal() {
    if (!finished && !pulled) {
      doPull();
    }
  }

  private void doPull() {
    pulled = true;
    if (isBackpressureless()) {
      listener.onBackpressurelessPull();
    } else {
      listener.onPull();
    }
  }

  @Override
  public void cancel() {
    builtGraph.execute(() -> {
      subscriber = null;
      if (!finished) {
        finished = true;
        demand = 0;
        listener.onDownstreamFinish();
      }
    });
  }

  @Override
  public void push(T element) {
    Objects.requireNonNull(element, "Elements cannot be null");
    if (finished) {
      throw new IllegalStateException("Can't push after publisher is finished");
    } else if (demand <= 0) {
      throw new IllegalStateException("Push without pull");
    }
    pulled = false;
    if (demand != Long.MAX_VALUE) {
      demand -= 1;
    }
    subscriber.onNext(element);
    if (demand > 0) {
      builtGraph.enqueueSignal(this);
    }
  }

  @Override
  public void backpressurelessPush(T element) {
    Objects.requireNonNull(element, "Elements cannot be null");
    if (finished) {
      throw new IllegalStateException("Can't push after publisher is finished");
    } else if (!isBackpressureless()) {
      throw new IllegalStateException("Can't do backpressureless push without backpressureless pull");
    }
    subscriber.onNext(element);
  }

  @Override
  public boolean isAvailable() {
    return !finished && pulled;
  }

  @Override
  public void complete() {
    if (finished) {
      throw new IllegalStateException("Can't complete twice");
    } else {
      finished = true;
      demand = 0;
      if (subscriber != null) {
        subscriber.onComplete();
        subscriber = null;
      }
    }
  }

  @Override
  public boolean isClosed() {
    return finished;
  }

  @Override
  public void fail(Throwable error) {
    Objects.requireNonNull(error, "Error must not be null");
    if (finished) {
      throw new IllegalStateException("Can't complete twice");
    } else {
      finished = true;
      demand = 0;
      if (subscriber != null) {
        subscriber.onError(error);
        subscriber = null;
      } else {
        failure = error;
      }
    }
  }

  @Override
  public void setListener(OutletListener listener) {
    this.listener = Objects.requireNonNull(listener, "Listener must not be null");
  }
}
