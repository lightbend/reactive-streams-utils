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

package com.lightbend.streams.tck;

import com.lightbend.streams.ReactiveStreams;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
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
        .build(engine)), Arrays.asList(1, 3, 5));
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

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return Collections.singletonList(
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
