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

import org.reactivestreams.utils.CompletionBuilder;
import org.reactivestreams.utils.ReactiveStreams;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.Flow;

import static org.testng.Assert.assertEquals;

public class FilterStageVerification extends AbstractStageVerification {

  FilterStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void filterStageShouldFilterElements() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3, 4, 5, 6)
        .filter(i -> (i & 1) == 1)
        .toList()
        .build(engine)), List.of(1, 3, 5));
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void filterStageShouldPropagateRuntimeExceptions() {
    await(ReactiveStreams.of("foo")
        .filter(foo -> {
          throw new RuntimeException("failed");
        })
        .toList()
        .build(engine));
  }

  @Test
  public void filterStageShouldSupportSkip() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3, 4)
        .skip(2)
        .toList()
        .build(engine)), List.of(3, 4));
  }

  @Test
  public void filterStageShouldSupportDropWhile() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3, 4)
        .dropWhile(i -> i < 3)
        .toList()
        .build(engine)), List.of(3, 4));
  }

  @Test
  public void filterStageShouldInstantiatePredicateOncePerRun() {
    CompletionBuilder<List<Integer>> completion =
        ReactiveStreams.of(1, 2, 3, 4, 5, 6)
            .skip(3)
            .toList();

    assertEquals(await(completion.build(engine)), List.of(4, 5, 6));
    assertEquals(await(completion.build(engine)), List.of(4, 5, 6));
  }


  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(
        new ProcessorVerification()
    );
  }

  class ProcessorVerification extends StageProcessorVerification<Integer> {

    @Override
    protected Flow.Processor<Integer, Integer> createIdentityFlowProcessor(int bufferSize) {
      return ReactiveStreams.<Integer>builder().filter(i -> true).build(engine);
    }

    @Override
    protected Flow.Publisher<Integer> createFailedFlowPublisher() {
      return ReactiveStreams.<Integer>failed(new RuntimeException("failed"))
          .filter(i -> true).build(engine);
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }
}
