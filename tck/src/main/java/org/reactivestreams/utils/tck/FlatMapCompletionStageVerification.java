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
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;

public class FlatMapCompletionStageVerification extends AbstractStageVerification {
  FlatMapCompletionStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void flatMapCsStageShouldMapFutures() throws Exception {
    CompletableFuture<Integer> one = new CompletableFuture<>();
    CompletableFuture<Integer> two = new CompletableFuture<>();
    CompletableFuture<Integer> three = new CompletableFuture<>();

    CompletionStage<List<Integer>> result = ReactiveStreams.of(one, two, three)
        .flatMapCompletionStage(Function.identity())
        .toList()
        .build(engine);

    Thread.sleep(100);

    one.complete(1);
    two.complete(2);
    three.complete(3);

    assertEquals(await(result), List.of(1, 2, 3));
  }

  @Test
  public void flatMapCsStageShouldMaintainOrderOfFutures() throws Exception {
    CompletableFuture<Integer> one = new CompletableFuture<>();
    CompletableFuture<Integer> two = new CompletableFuture<>();
    CompletableFuture<Integer> three = new CompletableFuture<>();

    CompletionStage<List<Integer>> result = ReactiveStreams.of(one, two, three)
        .flatMapCompletionStage(Function.identity())
        .toList()
        .build(engine);

    three.complete(3);
    Thread.sleep(100);
    two.complete(2);
    Thread.sleep(100);
    one.complete(1);

    assertEquals(await(result), List.of(1, 2, 3));
  }

  @Test
  public void flatMapCsStageShouldOnlyMapOneElementAtATime() throws Exception {
    CompletableFuture<Integer> one = new CompletableFuture<>();
    CompletableFuture<Integer> two = new CompletableFuture<>();
    CompletableFuture<Integer> three = new CompletableFuture<>();

    AtomicInteger concurrentMaps = new AtomicInteger(0);

    CompletionStage<List<Integer>> result = ReactiveStreams.of(one, two, three)
        .flatMapCompletionStage(i -> {
          assertEquals(1, concurrentMaps.incrementAndGet());
          return i;
        })
        .toList()
        .build(engine);

    Thread.sleep(100);
    concurrentMaps.decrementAndGet();
    one.complete(1);
    Thread.sleep(100);
    concurrentMaps.decrementAndGet();
    two.complete(2);
    Thread.sleep(100);
    concurrentMaps.decrementAndGet();
    three.complete(3);

    assertEquals(await(result), List.of(1, 2, 3));
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(new ProcessorVerification());
  }

  public class ProcessorVerification extends StageProcessorVerification<Integer> {
    @Override
    protected Flow.Processor<Integer, Integer> createIdentityFlowProcessor(int bufferSize) {
      return ReactiveStreams.<Integer>builder()
          .flatMapCompletionStage(CompletableFuture::completedFuture)
          .build(engine);
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }
}
