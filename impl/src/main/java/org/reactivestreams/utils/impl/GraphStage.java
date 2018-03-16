package org.reactivestreams.utils.impl;

import org.reactivestreams.utils.spi.Graph;

import java.util.concurrent.Executor;

abstract class GraphStage {

  private final GraphLogic graphLogic;

  GraphStage(GraphLogic graphLogic) {
    this.graphLogic = graphLogic;
  }

  protected <T> GraphLogic.SubStageInlet<T> createSubInlet(Graph graph) {
    return graphLogic.buildInlet(graph);
  }

  protected Executor executor() {
    return graphLogic;
  }

  /**
   * Run a callback after the graph has started.
   * <p>
   * When implementing this, it's important to remember that this is executed *after* the graph has started. It's
   * possible that the stage will receive other signals before this is executed, which may have been triggered from
   * the postStart methods on other stages. So this should not be used to do initialisation that should be done
   * before the stage is ready to receive signals, that initialisation should be done in the constructor, rather,
   * this can be used to initiate signals, but care needs to be taken, for example, a stage that just completes
   * immediately should check whether the outlet is completed first, since it may have been by a previous callback.
   */
  protected void postStart() {
    // Do nothing by default
  }

}
