package org.reactivestreams.utils.impl;

import java.util.concurrent.Flow;

public class ConnectorStage<T> extends GraphStage {
  private final Flow.Publisher<T> publisher;
  private final Flow.Subscriber<T> subscriber;

  public ConnectorStage(GraphLogic graphLogic, Flow.Publisher<T> publisher, Flow.Subscriber<T> subscriber) {
    super(graphLogic);
    this.publisher = publisher;
    this.subscriber = subscriber;
  }

  @Override
  protected void postStart() {
    publisher.subscribe(subscriber);
  }
}
