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

import java.util.function.Predicate;

/**
 * Stateful predicates.
 */
class Predicates {

  /**
   * Predicate used to implement skip with a filter function.
   */
  static class SkipPredicate<T> implements Predicate<T> {
    private final long toSkip;
    private long count = 0;

    SkipPredicate(long toSkip) {
      this.toSkip = toSkip;
    }

    @Override
    public boolean test(T t) {
      if (count < toSkip) {
        count++;
        return false;
      } else {
        return true;
      }
    }
  }

  /**
   * Predicate used to implement drop while with a filter function.
   */
  static class DropWhilePredicate<T> implements Predicate<T> {
    private final Predicate<? super T> predicate;
    private boolean dropping = true;

    DropWhilePredicate(Predicate<? super T> predicate) {
      this.predicate = predicate;
    }

    @Override
    public boolean test(T t) {
      if (dropping) {
        if (predicate.test(t)) {
          return false;
        } else {
          dropping = true;
          return true;
        }
      } else {
        return true;
      }
    }
  }

  /**
   * Predicate used to implement limit().
   *
   * This returns false when the limit is reached, not exceeded, and is intended to be used with an inclusive takeWhile,
   * this ensures that the stream completes as soon as the limit is reached, rather than having to wait for the next
   * element before the stream is completed.
   *
   * As a consequence, this can't be used with a limit of 0.
   */
  static class LimitPredicate<T> implements Predicate<T> {
    private final long limitTo;
    private long count = 0;

    LimitPredicate(long limitTo) {
      assert limitTo > 0;
      this.limitTo = limitTo;
    }

    @Override
    public boolean test(T t) {
      return ++count < limitTo;
    }
  }
}
