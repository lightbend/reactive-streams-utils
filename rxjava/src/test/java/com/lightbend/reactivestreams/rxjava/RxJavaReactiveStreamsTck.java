package com.lightbend.reactivestreams.rxjava;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.utils.tck.ReactiveStreamsTck;

public class RxJavaReactiveStreamsTck extends ReactiveStreamsTck<RxJavaEngine> {

  public RxJavaReactiveStreamsTck() {
    super(new TestEnvironment());
  }

  @Override
  protected RxJavaEngine createEngine() {
    return new RxJavaEngine();
  }
}
