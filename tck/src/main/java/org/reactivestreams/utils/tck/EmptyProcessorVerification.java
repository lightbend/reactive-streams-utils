package org.reactivestreams.utils.tck;

import org.reactivestreams.utils.ReactiveStreams;

import java.util.List;
import java.util.concurrent.Flow;

public class EmptyProcessorVerification extends AbstractStageVerification {

  public EmptyProcessorVerification(ReactiveStreamsTck.VerificationDeps deps) {
    super(deps);
  }

  @Override
  List<Object> reactiveStreamsTckVerifiers() {
    return List.of(new ProcessorVerification());
  }

  public class ProcessorVerification extends StageProcessorVerification<Integer> {
    @Override
    protected Flow.Processor<Integer, Integer> createIdentityFlowProcessor(int bufferSize) {
      return ReactiveStreams.<Integer>builder().build(engine);
    }

    @Override
    public Integer createElement(int element) {
      return element;
    }
  }
}
