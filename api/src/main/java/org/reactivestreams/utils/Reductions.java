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

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.stream.Collector;

/**
 * Reduction utilities that convert arguments supplied to reduce methods on the builders to Collectors.
 */
class Reductions {

  static <T> Collector<T, ?, Optional<T>> reduce(BinaryOperator<T> reducer) {

    return Collector.of(Reduction<T>::new,
        (r, t) -> {
          if (r.value == null) {
            r.value = t;
          } else {
            r.value = reducer.apply(r.value, t);
          }
        },
        (r, s) -> {
          if (r.value == null) {
            return r.replace(s.value);
          } else if (s.value != null) {
            return r.replace(reducer.apply(r.value, s.value));
          } else {
            return r;
          }
        },
        r -> {
          if (r.value == null) {
            return Optional.empty();
          } else {
            return Optional.of(r.value);
          }
        }
    );
  }

  static <T> Collector<T, ?, T> reduce(T identity, BinaryOperator<T> reducer) {

    return Collector.of(() -> new Reduction<>(identity),
        (r, t) -> r.value = reducer.apply(r.value, t),
        (r, s) -> r.replace(reducer.apply(r.value, s.value)),
        r -> r.value
    );
  }

  static <T, S> Collector<T, ?, S> reduce(S identity,
      BiFunction<S, ? super T, S> accumulator,
      BinaryOperator<S> combiner) {

    return Collector.of(() -> new Reduction<>(identity),
        (r, t) -> r.value = accumulator.apply(r.value, t),
        (r, s) -> r.replace(combiner.apply(r.value, s.value)),
        r -> r.value
    );
  }

  private static class Reduction<T> {
    T value;

    Reduction() {
    }

    Reduction(T value) {
      this.value = value;
    }

    Reduction<T> replace(T newValue) {
      this.value = newValue;
      return this;
    }
  }

}
