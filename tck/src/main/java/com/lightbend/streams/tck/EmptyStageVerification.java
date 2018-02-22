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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Flow;

import static org.testng.Assert.assertEquals;

public class EmptyStageVerification extends AbstractStageVerification {
  EmptyStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void emptyStageShouldProduceEmptyStream() {
    assertEquals(await(ReactiveStreams.empty().toList().build(engine)), Collections.emptyList());
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return Collections.singletonList(new PublisherVerification());
  }

  class PublisherVerification extends StagePublisherVerification<String> {
    @Override
    public Flow.Publisher<String> createFlowPublisher(long elements) {
      return ReactiveStreams.<String>empty().build(engine);
    }

    @Override
    public long maxElementsFromPublisher() {
      return 0;
    }
  }
}
