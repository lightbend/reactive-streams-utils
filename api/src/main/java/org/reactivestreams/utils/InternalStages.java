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

package org.reactivestreams.utils;

import org.reactivestreams.utils.spi.Stage;

/**
 * Internal stages, used to capture the graph while being built, but never passed to a
 * {@link ReactiveStreamsEngine}.
 */
class InternalStages {

  interface InternalStage extends Stage {}

  /**
   * An identity stage - this stage simply passes is input to its output unchanged. It's used to represent processor
   * builders that have had no stages defined.
   * <p>
   * It gets ignored by the {@link ReactiveStreamsBuilder} when encountered.
   */
  static final class Identity implements InternalStage {
    private Identity() {
    }

    static final Identity INSTANCE = new Identity();
  }

  /**
   * A nested stage. This is used to avoid having to rebuild the entire graph (which is represented as an immutable
   * cons) whenever two graphs are joined, or a stage is prepended into the graph.
   * <p>
   * It gets flattened out by the {@link ReactiveStreamsBuilder} when building the graph.
   */
  static final class Nested implements InternalStage {
    private final ReactiveStreamsBuilder stage;

    Nested(ReactiveStreamsBuilder stage) {
      this.stage = stage;
    }

    ReactiveStreamsBuilder getBuilder() {
      return stage;
    }
  }
}
