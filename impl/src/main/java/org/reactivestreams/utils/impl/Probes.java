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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Flow.*;

public class Probes {

  private static final AtomicLong counter = new AtomicLong(1000);

  public static <T> Subscriber<T> subscriber(String name, Subscriber<T> probed) {
    return new SubscriberProbe<>(name + "-" + counter.getAndIncrement(), probed);
  }

  public static <T> Publisher<T> publisher(String name, Publisher<T> probed) {
    return new PublisherProbe<>(name + "-" + counter.getAndIncrement(), probed);
  }

  private static class Probe {
    private final String name;

    protected Probe(String name) {
      this.name = name;
    }

    protected void trace(String methodName, Object description, Runnable operation) {
      log("ENTER " + methodName + " " + description);
      try {
        operation.run();
        log("LEAVE " + methodName);
      } catch (RuntimeException e) {
        log("ERROR " + methodName);
        e.printStackTrace();
        throw e;
      }
    }

    protected void log(String msg) {
      System.out.println(System.currentTimeMillis() + " " + name + " - " + msg);
    }
  }

  private static class SubscriptionProbe extends Probe implements Subscription {
    private final Subscription probed;

    public SubscriptionProbe(String name, Subscription probed) {
      super(name);
      this.probed = probed;
    }

    @Override
    public void request(long n) {
      trace("request", n, () -> probed.request(n));
    }

    @Override
    public void cancel() {
      trace("cancel", "", () -> probed.cancel());
    }
  }

  private static class SubscriberProbe<T> extends Probe implements Subscriber<T> {

    private final String name;
    private final Subscriber<T> probed;

    public SubscriberProbe(String name, Subscriber<T> probed) {
      super(name);
      this.name = name;
      this.probed = probed;
    }

    @Override
    public void onSubscribe(Subscription s) {
      trace("onSubscribe", s, () -> {
        if (s == null) {
          probed.onSubscribe(s);
        } else {
          probed.onSubscribe(new SubscriptionProbe(name, s));
        }
      });
    }

    @Override
    public void onNext(T t) {
      trace("onNext", t, () -> probed.onNext(t));
    }

    @Override
    public void onError(Throwable t) {
      trace("onError", t, () -> probed.onError(t));
    }

    @Override
    public void onComplete() {
      trace("onComplete", "", () -> probed.onComplete());
    }
  }

  private static class PublisherProbe<T> extends Probe implements Publisher<T> {
    private final String name;
    private final Publisher<T> probed;

    public PublisherProbe(String name, Publisher<T> probed) {
      super(name);
      this.name = name;
      this.probed = probed;
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
      trace("subscribe", s, () -> {
        if (s == null) {
          probed.subscribe(s);
        } else {
          probed.subscribe(new SubscriberProbe<>(name, s));
        }
      });
    }
  }
}


