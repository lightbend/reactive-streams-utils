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

package org.reactivestreams.utils.tck;

import org.reactivestreams.utils.ReactiveStreams;
import org.reactivestreams.utils.SubscriberWithResult;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;

public class ForEachStageVerification extends AbstractStageVerification {

  ForEachStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void forEachStageShouldReceiveElements() {
    List<Integer> elements = new CopyOnWriteArrayList<>();
    await(ReactiveStreams.of(1, 2, 3)
        .forEach(elements::add).build(engine));
    assertEquals(elements, List.of(1, 2, 3));
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void forEachStageShouldPropagateErrors() {
    await(ReactiveStreams.failed(new RuntimeException("failed"))
        .forEach(e -> {}).build(engine));
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void forEachStageShouldHandleErrors() {
    await(ReactiveStreams.of(1)
        .forEach(e -> {
          throw new RuntimeException("failed");
        }).build(engine));
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(new SubscriberVerification());
  }

  public class SubscriberVerification extends StageSubscriberWhiteboxVerification<Integer> {

    @Override
    protected Flow.Subscriber<Integer> createFlowSubscriber(WhiteboxSubscriberProbe<Integer> probe) {
      SubscriberWithResult<Integer, Void> result =
          ReactiveStreams.<Integer>builder().forEach(probe::registerOnNext)
              .build(engine);

      result.getResult().whenComplete((success, error) -> {
        if (error != null) {
          // Need to look into why this ends up being wrapped in CompletionException, it probably shouldn't be.
          if (error instanceof CompletionException) {
            probe.registerOnError(error.getCause());
          } else {
            probe.registerOnError(error);
          }
        } else {
          probe.registerOnComplete();
        }
      });

      Flow.Subscriber<Integer> subscriber = result.getSubscriber();
      AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

      // We don't register this in the onSubscribe method below since the double subscribe
      // test will fail.
      probe.registerOnSubscribe(new SubscriberPuppet() {
        @Override
        public void triggerRequest(long elements) {
          // forEach implies a constant request.
        }
        @Override
        public void signalCancel() {
          subscription.get().cancel();
        }
      });

      // We wrap it so that we can capture the subscription to trigger cancellation.
      return new Flow.Subscriber<>() {
        @Override
        public void onSubscribe(Flow.Subscription s) {
          subscriber.onSubscribe(s);
          subscription.compareAndSet(null, s);
        }

        @Override
        public void onNext(Integer item) {
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
      };
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }
}
