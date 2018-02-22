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
import org.reactivestreams.utils.ReactiveStreamsEngine;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.IdentityProcessorVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;
import org.reactivestreams.tck.flow.FlowSubscriberBlackboxVerification;
import org.reactivestreams.tck.flow.FlowSubscriberWhiteboxVerification;

import java.util.List;
import java.util.concurrent.*;

abstract class AbstractStageVerification {

  final ReactiveStreamsEngine engine;
  final TestEnvironment environment;
  final ExecutorService executorService;

  AbstractStageVerification(ReactiveStreamsTck.VerificationDeps deps) {
    this.engine = deps.engine();
    this.environment = deps.testEnvironment();
    this.executorService = deps.executorService();
  }

  abstract List<Object> reactiveStreamsTckVerifiers();

  <T> T await(CompletionStage<T> future) {
    try {
      return future.toCompletableFuture().get(environment.defaultTimeoutMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      } else {
        throw new RuntimeException(e.getCause());
      }
    } catch (TimeoutException e) {
      throw new RuntimeException("Future timed out after " + environment.defaultTimeoutMillis() + "ms", e);
    }
  }

  abstract class StagePublisherVerification<T> extends FlowPublisherVerification<T> {

    StagePublisherVerification() {
      super(AbstractStageVerification.this.environment);
    }

    @Override
    public Flow.Publisher<T> createFailedFlowPublisher() {
      return ReactiveStreams.<T>failed(new RuntimeException("failed")).build(engine);
    }
  }

  /**
   * This uses IdentityProcessorVerification rather than IdentityFlowProcessorVerification due to
   * https://github.com/reactive-streams/reactive-streams-jvm/issues/425
   */
  abstract class StageProcessorVerification<T> extends IdentityProcessorVerification<T> {
    StageProcessorVerification() {
      super(AbstractStageVerification.this.environment);
    }

    @Override
    public ExecutorService publisherExecutorService() {
      return executorService;
    }

    protected Flow.Publisher<T> createFailedFlowPublisher() {
      return ReactiveStreams.<T>failed(new RuntimeException("failed")).build(engine);
    }

    protected abstract Flow.Processor<T, T> createIdentityFlowProcessor(int bufferSize);

    @Override
    public final Processor<T, T> createIdentityProcessor(int bufferSize) {
      return FlowAdapters.toProcessor(createIdentityFlowProcessor(bufferSize));
    }

    @Override
    public final Publisher<T> createFailedPublisher() {
      Flow.Publisher<T> failed = createFailedFlowPublisher();
      if (failed == null) return null;
      return FlowAdapters.toPublisher(failed);
    }

    @Override
    public long maxSupportedSubscribers() {
      return 1;
    }
  }

  abstract class StageSubscriberWhiteboxVerification<T> extends FlowSubscriberWhiteboxVerification<T> {
    public StageSubscriberWhiteboxVerification() {
      super(AbstractStageVerification.this.environment);
    }
  }

  abstract class StageSubscriberBlackboxVerification<T> extends FlowSubscriberBlackboxVerification<T> {
    StageSubscriberBlackboxVerification() {
      super(AbstractStageVerification.this.environment);
    }
  }
}
