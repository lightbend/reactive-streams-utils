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
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;

public class CollectStageVerification extends AbstractStageVerification {

  CollectStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void toListStageShouldReturnAList() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3)
        .toList().build(engine)), List.of(1, 2, 3));
  }

  @Test
  public void toListStageShouldReturnEmpty() {
    assertEquals(await(ReactiveStreams.of()
        .toList().build(engine)), List.of());
  }

  @Test
  public void finisherFunctionShouldBeInvoked() {
    assertEquals(await(ReactiveStreams.of("1", "2", "3")
        .collect(Collectors.joining(", ")).build(engine)), "1, 2, 3");
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void toListStageShouldPropagateErrors() {
    await(ReactiveStreams.failed(new RuntimeException("failed"))
        .toList().build(engine));
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(new SubscriberVerification());
  }

  class SubscriberVerification extends StageSubscriberBlackboxVerification<Integer> {
    @Override
    public Flow.Subscriber<Integer> createFlowSubscriber() {
      return ReactiveStreams.<Integer>builder().toList().build(engine).getSubscriber();
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }
}
