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
import java.util.concurrent.Flow;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;

public class MapStageVerification extends AbstractStageVerification {

  MapStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void mapStageShouldMapElements() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3)
        .map(Object::toString)
        .toList()
        .build(engine)), List.of("1", "2", "3"));
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void mapStageShouldPropagateRuntimeExceptions() {
    await(ReactiveStreams.of("foo")
        .map(foo -> {
          throw new RuntimeException("failed");
        })
        .toList()
        .build(engine));
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
      return ReactiveStreams.<Integer>builder().map(Function.identity()).build(engine);
    }

    @Override
    protected Flow.Publisher<Integer> createFailedFlowPublisher() {
      return ReactiveStreams.<Integer>failed(new RuntimeException("failed"))
          .map(Function.identity()).build(engine);
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }
}
