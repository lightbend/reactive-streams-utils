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

/**
 * The Reactive Streams utils SPI.
 *
 * Implementors are expected to implement the {@code ReactiveStreamsEngine} interface, and use this SPI to inspect
 * the graph of stages.
 *
 * A TCK is also provided that validates that an implementation is both correct according to this specification, and
 * the Reactive Streams specification.
 */
package org.reactivestreams.utils.spi;