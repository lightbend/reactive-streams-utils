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
import java.util.concurrent.Flow;

import static org.testng.Assert.assertEquals;

public class TakeWhileStageVerification extends AbstractStageVerification {

  TakeWhileStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void takeWhileStageShouldTakeWhileConditionIsTrue() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3, 4, 5, 6, 1, 2)
        .takeWhile(i -> i < 5)
        .toList()
        .build(engine)), List.of(1, 2, 3, 4));
  }

  @Test
  public void takeWhileStageShouldEmitEmpty() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3, 4, 5, 6)
        .takeWhile(i -> false)
        .toList()
        .build(engine)), List.of());
  }

  @Test
  public void takeWhileStageShouldSupportLimit() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3, 4, 5, 6)
        .limit(3)
        .toList()
        .build(engine)), List.of(1, 2, 3));
  }

  @Test
  public void takeWhileShouldCancelUpStreamWhenDone() {
    CompletableFuture<Void> cancelled = new CompletableFuture<>();
    ReactiveStreams.<Integer>fromPublisher(subscriber ->
        subscriber.onSubscribe(new Flow.Subscription() {
          @Override
          public void request(long n) {
            subscriber.onNext(1);
          }
          @Override
          public void cancel() {
            cancelled.complete(null);
          }
        })
    ).limit(1)
        .toList()
        .build(engine);
    await(cancelled);
  }

  @Test
  public void takeWhileShouldIgnoreSubsequentErrorsWhenDone() {
    assertEquals(await(
        ReactiveStreams.of(1, 2, 3, 4)
            .flatMap(i -> {
              if (i == 4) return ReactiveStreams.failed(new RuntimeException("failed"));
              else return ReactiveStreams.of(i);
            })
            .limit(3)
            .toList()
            .build(engine)
    ), List.of(1, 2, 3));
  }

  @Test
  public void takeWhileShouldSupportLimitingToZero() {
    assertEquals(await(
        ReactiveStreams.of(1, 2, 3, 4)
            .limit(0)
            .toList()
            .build(engine)
    ), List.of());
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(new ProcessorVerification());
  }

  public class ProcessorVerification extends StageProcessorVerification<Integer> {
    @Override
    protected Flow.Processor<Integer, Integer> createIdentityFlowProcessor(int bufferSize) {
      return ReactiveStreams.<Integer>builder()
          .takeWhile(t -> true)
          .build(engine);
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }
}
