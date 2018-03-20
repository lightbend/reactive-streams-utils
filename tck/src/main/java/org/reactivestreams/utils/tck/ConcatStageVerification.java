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
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.testng.Assert.assertEquals;

public class ConcatStageVerification extends AbstractStageVerification {

  ConcatStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void concatStageShouldConcatTwoGraphs() {
    assertEquals(await(
        ReactiveStreams.concat(
            ReactiveStreams.of(1, 2, 3),
            ReactiveStreams.of(4, 5, 6)
        )
            .toList()
            .build(engine)
    ), List.of(1, 2, 3, 4, 5, 6));
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void concatStageShouldCancelSecondStageIfFirstFails() {
    CancelCapturingPublisher<Integer> cancelCapture = new CancelCapturingPublisher<>();

    CompletionStage<Void> completion = ReactiveStreams.concat(
        ReactiveStreams.failed(new RuntimeException("failed")),
        ReactiveStreams.fromPublisher(cancelCapture)
    )
        .forEach(e -> {})
        .build(engine);

    await(cancelCapture.getCancelled());
    await(completion);
  }

  @Test
  public void concatStageShouldCancelSecondStageIfFirstCancellationOccursDuringFirst() {
    CancelCapturingPublisher<Integer> cancelCapture = new CancelCapturingPublisher<>();

    CompletionStage<List<Integer>> result = ReactiveStreams.concat(
        ReactiveStreams.fromIterable(() -> IntStream.range(1, 1000000).boxed().iterator()),
        ReactiveStreams.fromPublisher(cancelCapture)
    )
        .limit(5)
        .toList()
        .build(engine);

    await(cancelCapture.getCancelled());
    assertEquals(await(result), List.of(1, 2, 3, 4, 5));
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(new PublisherVerification());
  }

  class PublisherVerification extends StagePublisherVerification<Long> {
    @Override
    public Flow.Publisher<Long> createFlowPublisher(long elements) {
      long toEmitFromFirst = elements / 2;

      return ReactiveStreams.concat(
          ReactiveStreams.fromIterable(
              () -> LongStream.rangeClosed(1, toEmitFromFirst).boxed().iterator()
          ),
          ReactiveStreams.fromIterable(
              () -> LongStream.rangeClosed(toEmitFromFirst + 1, elements).boxed().iterator()
          )
      ).build(engine);
    }
  }

  private static class CancelCapturingPublisher<T> implements Flow.Publisher<T> {
    private final CompletableFuture<T> cancelled = new CompletableFuture<>();

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
      subscriber.onSubscribe(new Flow.Subscription() {
        @Override
        public void request(long n) {
        }
        @Override
        public void cancel() {
          cancelled.complete(null);
        }
      });
    }

    public CompletableFuture<T> getCancelled() {
      return cancelled;
    }
  }

}
