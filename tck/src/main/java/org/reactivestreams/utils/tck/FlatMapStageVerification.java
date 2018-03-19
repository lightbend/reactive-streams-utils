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

package org.reactivestreams.utils.tck;

import org.reactivestreams.utils.ReactiveStreams;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;

public class FlatMapStageVerification extends AbstractStageVerification {
  FlatMapStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void flatMapStageShouldMapElements() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3)
        .flatMap(n -> ReactiveStreams.of(n, n, n))
        .toList()
        .build(engine)), List.of(1, 1, 1, 2, 2, 2, 3, 3, 3));
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void flatMapStageShouldPropagateRuntimeExceptions() {
    await(ReactiveStreams.of("foo")
        .flatMap(foo -> {
          throw new RuntimeException("failed");
        })
        .toList()
        .build(engine));
  }

  @Test
  public void flatMapStageShouldOnlySubscribeToOnePublisherAtATime() throws Exception {
    AtomicInteger activePublishers = new AtomicInteger();

    // A publisher that publishes one element 100ms after being requested,
    // and then completes 100ms later. It also uses activePublishers to ensure
    // that it is the only publisher that is subscribed to at any one time.
    class ScheduledPublisher implements Flow.Publisher<Integer> {
      private final int id;
      ScheduledPublisher(int id) {
        this.id = id;
      }
      private AtomicBoolean published = new AtomicBoolean(false);
      @Override
      public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
        assertEquals(activePublishers.incrementAndGet(), 1);
        subscriber.onSubscribe(new Flow.Subscription() {
          @Override
          public void request(long n) {
            if (published.compareAndSet(false, true)) {
              executorService.schedule(() -> {
                subscriber.onNext(id);
                executorService.schedule(() -> {
                  activePublishers.decrementAndGet();
                  subscriber.onComplete();
                }, 100, TimeUnit.MILLISECONDS);
              }, 100, TimeUnit.MILLISECONDS);
            }
          }
          @Override
          public void cancel() {
          }
        });
      }
    }

    CompletionStage<List<Integer>> result = ReactiveStreams.of(1, 2, 3, 4, 5)
        .flatMap(id -> ReactiveStreams.fromPublisher(new ScheduledPublisher(id)))
        .toList()
        .build(engine);

    assertEquals(result.toCompletableFuture().get(2, TimeUnit.SECONDS),
        List.of(1, 2, 3, 4, 5));
  }


  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(new OuterProcessorVerification(), new InnerSubscriberVerification());
  }

  /**
   * Verifies the outer processor.
   */
  public class OuterProcessorVerification extends StageProcessorVerification<Integer> {

    @Override
    protected Flow.Processor<Integer, Integer> createIdentityFlowProcessor(int bufferSize) {
      return ReactiveStreams.<Integer>builder().flatMap(ReactiveStreams::of).build(engine);
    }

    @Override
    protected Flow.Publisher<Integer> createFailedFlowPublisher() {
      return ReactiveStreams.<Integer>failed(new RuntimeException("failed"))
          .flatMap(ReactiveStreams::of).build(engine);
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }

  /**
   * Verifies the inner subscriber passed to publishers produced by the mapper function.
   */
  public class InnerSubscriberVerification extends StageSubscriberWhiteboxVerification<Integer> {

    @Override
    protected Flow.Subscriber<Integer> createFlowSubscriber(WhiteboxSubscriberProbe<Integer> probe) {
      CompletableFuture<Flow.Subscriber<? super Integer>> subscriber = new CompletableFuture<>();
      ReactiveStreams.of(ReactiveStreams.<Integer>fromPublisher(subscriber::complete))
          .flatMap(Function.identity())
          .to(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
              // We need to initially request an element to ensure that we get the publisher.
              subscription.request(1);
              probe.registerOnSubscribe(new SubscriberPuppet() {
                @Override
                public void triggerRequest(long elements) {
                  subscription.request(elements);
                }

                @Override
                public void signalCancel() {
                  subscription.cancel();
                }
              });
            }

            @Override
            public void onNext(Integer item) {
              probe.registerOnNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
              probe.registerOnError(throwable);
            }

            @Override
            public void onComplete() {
              probe.registerOnComplete();
            }
          })
          .build(engine);

      return (Flow.Subscriber) await(subscriber);
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }
}
