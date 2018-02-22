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

/**
 * Exception thrown when a reactive streams engine doesn't support a stage that is passed to it.
 * <p>
 * All reactive streams engines should support all stages, but this allows for a graceful mechanism to report issues,
 * for example if in a future version a new stage is added that is not recognised by an existing implementation.
 */
public class UnsupportedStageException extends RuntimeException {
  public UnsupportedStageException(Stage stage) {
    super("The " + stage.getClass().getSimpleName() + " stage is not supported by this Reactive Streams engine.");
  }
}
