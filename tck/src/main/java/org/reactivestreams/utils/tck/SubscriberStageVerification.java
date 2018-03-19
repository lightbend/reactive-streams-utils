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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public class SubscriberStageVerification extends AbstractStageVerification {
  SubscriberStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void subscriberStageShouldRedeemCompletionStageWhenCompleted() {
    CompletionStage<Void> result = ReactiveStreams.of().to(
        ReactiveStreams.builder().ignore().build(engine).getSubscriber()
    ).build(engine);
    await(result);
  }

  @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "failed")
  public void subscriberStageShouldRedeemCompletionStageWhenFailed() {
    CompletionStage<Void> result = ReactiveStreams.failed(new RuntimeException("failed")).to(
        ReactiveStreams.builder().ignore().build(engine).getSubscriber()
    ).build(engine);
    await(result);
  }

  @Test(expectedExceptions = CancellationException.class)
  public void subscriberStageShouldRedeemCompletionStageWithCancellationExceptionWhenCancelled() {
    CompletionStage<Void> result = ReactiveStreams.fromPublisher(subscriber -> {
      subscriber.onSubscribe(new Flow.Subscription() {
        @Override
        public void request(long n) {
        }
        @Override
        public void cancel() {
        }
      });
    }).to(
        ReactiveStreams.builder().cancel().build(engine).getSubscriber()
    ).build(engine);
    await(result);
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of();
  }
}
