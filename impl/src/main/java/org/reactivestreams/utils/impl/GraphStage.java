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

package org.reactivestreams.utils.impl;

import org.reactivestreams.utils.spi.Graph;

import java.util.concurrent.Executor;

/**
 * Superclass of all graph stages.
 */
abstract class GraphStage {

  private final BuiltGraph builtGraph;

  GraphStage(BuiltGraph builtGraph) {
    this.builtGraph = builtGraph;
  }

  /**
   * Create a sub inlet for the given graph.
   * <p>
   * After being created, the inlet should have an inlet listener attached to it, and then it should be started.
   *
   * @param graph The graph.
   * @return The inlet.
   */
  protected <T> BuiltGraph.SubStageInlet<T> createSubInlet(Graph graph) {
    return builtGraph.buildSubInlet(graph);
  }

  protected Executor executor() {
    return builtGraph;
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
