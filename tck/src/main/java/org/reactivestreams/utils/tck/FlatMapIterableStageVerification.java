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
import java.util.concurrent.Flow;

import static org.testng.Assert.assertEquals;

public class FlatMapIterableStageVerification extends AbstractStageVerification {
  FlatMapIterableStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void flatMapIterableStageShouldMapElements() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3)
        .flatMapIterable(n -> List.of(n, n, n))
        .toList()
        .build(engine)), List.of(1, 1, 1, 2, 2, 2, 3, 3, 3));
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void flatMapIterableStageShouldPropagateRuntimeExceptions() {
    await(ReactiveStreams.of("foo")
        .flatMapIterable(foo -> {
          throw new RuntimeException("failed");
        })
        .toList()
        .build(engine));
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(new ProcessorVerification());
  }

  /**
   * Verifies the outer processor.
   */
  public class ProcessorVerification extends StageProcessorVerification<Integer> {

    @Override
    protected Flow.Processor<Integer, Integer> createIdentityFlowProcessor(int bufferSize) {
      return ReactiveStreams.<Integer>builder().flatMapIterable(List::of).build(engine);
    }

    @Override
    protected Flow.Publisher<Integer> createFailedFlowPublisher() {
      return ReactiveStreams.<Integer>failed(new RuntimeException("failed"))
          .flatMapIterable(List::of).build(engine);
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }

}
