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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;

import static org.testng.Assert.assertEquals;

public class FindFirstStageVerification extends AbstractStageVerification {

  FindFirstStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void findFirstStageShouldFindTheFirstElement() {
    assertEquals(await(ReactiveStreams.of(1, 2, 3)
        .findFirst().build(engine)), Optional.of(1));
  }

  @Test
  public void findFirstStageShouldReturnEmpty() {
    assertEquals(await(ReactiveStreams.of()
        .findFirst().build(engine)), Optional.empty());
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void findFirstStageShouldPropagateErrors() {
    await(ReactiveStreams.failed(new RuntimeException("failed"))
        .findFirst().build(engine));
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return Collections.singletonList(new SubscriberVerification());
  }

  class SubscriberVerification extends StageSubscriberBlackboxVerification<Integer> {
    @Override
    public Flow.Subscriber<Integer> createFlowSubscriber() {
      return ReactiveStreams.<Integer>builder().findFirst().build(engine).getSubscriber();
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }
}
