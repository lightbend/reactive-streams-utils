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

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.utils.tck.CollectProcessorStageVerification;
import org.reactivestreams.utils.tck.ReactiveStreamsTck;

public class ReactiveStreamsEngineImplTck extends ReactiveStreamsTck<ReactiveStreamsEngineImpl> {

  public ReactiveStreamsEngineImplTck() {
    super(new TestEnvironment(100));
  }

  @Override
  protected ReactiveStreamsEngineImpl createEngine() {
    return new ReactiveStreamsEngineImpl();
  }
}
