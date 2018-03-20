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

package com.lightbend.reactivestreams.rxjava;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.utils.tck.ReactiveStreamsTck;

public class RxJavaReactiveStreamsTck extends ReactiveStreamsTck<RxJavaEngine> {

  public RxJavaReactiveStreamsTck() {
    super(new TestEnvironment());
  }

  @Override
  protected RxJavaEngine createEngine() {
    return new RxJavaEngine();
  }
}
