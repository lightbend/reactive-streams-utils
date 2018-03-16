package org.reactivestreams.utils.impl;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.utils.tck.ReactiveStreamsTck;

public class ReactiveStreamsEngineImplTck extends ReactiveStreamsTck<ReactiveStreamsEngineImpl> {

  public ReactiveStreamsEngineImplTck() {
    super(new TestEnvironment(100));
  }

  @Override
  protected ReactiveStreamsEngineImpl createEngine() {
    return new ReactiveStreamsEngineImpl();
  }

}
