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

package org.reactivestreams.utils;

import org.reactivestreams.utils.spi.Graph;
import org.reactivestreams.utils.spi.Stage;

import java.util.*;

/**
 * Superclass of all reactive streams builders.
 *
 * @param <S> The shape of the graph being built.
 * @see ReactiveStreams
 */
public abstract class ReactiveStreamsBuilder<S> {

  private final Stage stage;
  private final ReactiveStreamsBuilder<?> previous;

  ReactiveStreamsBuilder(Stage stage, ReactiveStreamsBuilder<?> previous) {
    this.stage = stage;
    this.previous = previous;
  }

  /**
   * Build this stream, using the first {@link ReactiveStreamsEngine} found by the {@link ServiceLoader}.
   *
   * @return The graph of the given shape.
   */
  public S build() {
    Optional<ReactiveStreamsEngine> engine = ServiceLoader.load(ReactiveStreamsEngine.class).findFirst();

    if (engine.isPresent()) {
      return build(engine.get());
    } else {
      throw new IllegalStateException("No implementation of ReactiveStreamsEngine service could be found.");
    }
  }

  /**
   * Build this stream, using the supplied {@link ReactiveStreamsEngine}.
   *
   * @param engine The engine to build the stream with.
   * @return The graph of the given shape.
   */
  public abstract S build(ReactiveStreamsEngine engine);

  Graph toGraph(boolean expectInlet, boolean expectOutlet) {
    ArrayDeque<Stage> deque = new ArrayDeque<>();
    flatten(deque);
    Graph graph = new Graph(Collections.unmodifiableCollection(deque));

    if (expectInlet) {
      if (!graph.hasInlet()) {
        throw new IllegalStateException("Expected to build a graph with an inlet, but no inlet was found: " + graph);
      }
    } else if (graph.hasInlet()) {
      throw new IllegalStateException("Expected to build a graph with no inlet, but an inlet was found: " + graph);
    }

    if (expectOutlet) {
      if (!graph.hasOutlet()) {
        throw new IllegalStateException("Expected to build a graph with an outlet, but no outlet was found: " + graph);
      }
    } else if (graph.hasOutlet()) {
      throw new IllegalStateException("Expected to build a graph with no outlet, but an outlet was found: " + graph);
    }

    return graph;
  }

  private void flatten(Deque<Stage> stages) {
    if (stage == InternalStages.Identity.INSTANCE) {
      // Ignore, no need to add an identity stage
    } else if (stage instanceof InternalStages.Nested) {
      ((InternalStages.Nested) stage).getBuilder().flatten(stages);
    } else {
      stages.addFirst(stage);
    }

    if (previous != null) {
      previous.flatten(stages);
    }
  }

}
