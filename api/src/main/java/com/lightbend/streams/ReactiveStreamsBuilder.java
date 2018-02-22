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

package com.lightbend.streams;

import com.lightbend.streams.spi.Stage;

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

  Iterable<Stage> flatten() {
    ArrayDeque<Stage> deque = new ArrayDeque<>();
    flatten(deque);
    return Collections.unmodifiableCollection(deque);
  }

  /**
   * Verifies the invariants of the list of stages passed to the ReactiveStreamsEngine methods.
   * <p>
   * This adds nothing to the functionality of the API, except for a sanity check of the APIs own operations, to catch
   * bugs etc. It ensures that the graph makes sense - eg, that the adjacent stages have an outlet and an inlet, and
   * will throw an error for example if you have a publisher followed by another publisher in the graph.
   */
  Iterable<Stage> verify(Iterable<Stage> stages, boolean requireInlet, boolean requireOutlet) {

    boolean expectInlet = requireInlet;
    Stage lastStage = null;

    for (Stage stage : stages) {
      if (lastStage != null && !lastStage.hasOutlet()) {
        throw new IllegalStateException("Graph required an outlet from the previous stage " + lastStage + " but none was found.");
      }

      if (expectInlet) {
        if (!stage.hasInlet()) {
          throw new IllegalStateException("Graph required an inlet, but had " + stage + " instead.");
        }
      } else {
        if (stage.hasInlet()) {
          throw new IllegalStateException("Graph should not start with an inlet, but found one at " + stage);
        }
        expectInlet = true;
      }

      lastStage = stage;
    }

    if (lastStage == null) {
      // Special case, empty graph
      if (!requireOutlet || !requireInlet) {
        throw new IllegalStateException("Graph with empty stages must have an inlet and an outlet");
      }
    } else if (requireOutlet) {
      if (!lastStage.hasOutlet()) {
        throw new IllegalStateException("Graph should terminate with an outlet, but none was found at " + lastStage);
      }
    } else {
      if (lastStage.hasOutlet()) {
        throw new IllegalStateException("Graph should terminate with no outlet, but an outlet was found at " + lastStage);
      }
    }

    return stages;
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
