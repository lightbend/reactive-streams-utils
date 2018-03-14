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

package org.reactivestreams.utils.spi;

import java.util.Collection;

/**
 * A graph.
 *
 * Reactive Streams engines are required to convert the stages of this graph into a stream with interfaces according
 * to the shape. The shape is governed by whether the graph has an inlet, an outlet, neither or both.
 */
public class Graph {
  private final Collection<Stage> stages;
  private final boolean hasInlet;
  private final boolean hasOutlet;

  /**
   * Create a graph from the given stages.
   *
   * The stages of this graph will be validated to ensure that connect properly, that is, every stage but the first
   * stage must have an inlet to receive a stream from the previous stage, and every stage but the last stage must have
   * an outlet to produce a stream from the next stage.
   *
   * If the first stage has an inlet, then this graph has an inlet, and can therefore be represented as a
   * {@link java.util.concurrent.Flow.Subscriber}. If the last stage has an outlet, then this graph has an outlet, and
   * therefore can be represented as a {@link java.util.concurrent.Flow.Publisher}.
   *
   * @param stages The stages.
   */
  public Graph(Collection<Stage> stages) {

    boolean hasInlet = true;
    Stage lastStage = null;

    for (Stage stage : stages) {
      if (lastStage != null && !lastStage.hasOutlet()) {
        throw new IllegalStateException("Graph required an outlet from the previous stage " + lastStage + " but none was found.");
      }

      if (lastStage != null) {
        if (!stage.hasInlet()) {
          throw new IllegalStateException("Stage encountered in graph with no inlet after the first stage: " + stage);
        }
      } else {
        hasInlet = stage.hasInlet();
      }

      lastStage = stage;
    }

    this.hasInlet = hasInlet;
    this.hasOutlet = lastStage == null || lastStage.hasOutlet();
    this.stages = stages;
  }

  /**
   * Get the stages of this graph.
   */
  public Collection<Stage> getStages() {
    return stages;
  }

  /**
   * Returns true if this graph has an inlet, ie, if this graph can be turned into a
   * {@link java.util.concurrent.Flow.Subscriber}.
   */
  public boolean hasInlet() {
    return hasInlet;
  }

  /**
   * Returns true if this graph has an outlet, ie, if this graph can be turned into a
   * {@link java.util.concurrent.Flow.Publisher}.
   */
  public boolean hasOutlet() {
    return hasOutlet;
  }

  @Override
  public String toString() {
    return "Graph{" +
        "stages=" + stages +
        '}';
  }
}
