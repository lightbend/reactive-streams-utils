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
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public class CancelStageVerification extends AbstractStageVerification {
  CancelStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Test
  public void cancelStageShouldCancelTheStage() {
    CompletableFuture<Void> cancelled = new CompletableFuture<>();
    CompletionStage<Void> result = ReactiveStreams.fromPublisher(s -> {
      s.onSubscribe(new Flow.Subscription() {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
          cancelled.complete(null);
        }
      });
    }).cancel().build(engine);
    await(cancelled);
    await(result);
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(new SubscriberVerification());
  }

  public class SubscriberVerification extends StageSubscriberBlackboxVerification {
    @Override
    public Flow.Subscriber createFlowSubscriber() {
      return ReactiveStreams.builder().cancel().build(engine).getSubscriber();
    }

    @Override
    public Object createElement(int element) {
      return element;
    }

    @Override
    public void required_spec201_blackbox_mustSignalDemandViaSubscriptionRequest() throws Throwable {
      throw new SkipException("Cancel subscriber does not need to signal demand.");
    }

    @Override
    public void required_spec209_blackbox_mustBePreparedToReceiveAnOnCompleteSignalWithPrecedingRequestCall() throws Throwable {
      throw new SkipException("Cancel subscriber does not need to signal demand.");
    }

    @Override
    public void required_spec210_blackbox_mustBePreparedToReceiveAnOnErrorSignalWithPrecedingRequestCall() throws Throwable {
      throw new SkipException("Cancel subscriber does not need to signal demand.");
    }
  }
}
