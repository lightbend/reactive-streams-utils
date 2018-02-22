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

package com.lightbend.streams.akka.tck;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.lightbend.streams.ReactiveStreamsEngine;
import com.lightbend.streams.akka.AkkaEngine;
import com.lightbend.streams.tck.ReactiveStreamsTck;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterSuite;

/**
 * TCK verification for the {@link AkkaEngine} implementation of the {@link ReactiveStreamsEngine}.
 */
public class AkkaReactiveStreamsTck extends ReactiveStreamsTck<AkkaEngine> {

  public AkkaReactiveStreamsTck() {
    super(new TestEnvironment());
  }

  private ActorSystem system;

  @AfterSuite
  public void shutdownActorSystem() {
    if (system != null) {
      system.terminate();
    }
  }

  @Override
  protected AkkaEngine createEngine() {
    system = ActorSystem.create();
    Materializer materializer = ActorMaterializer.create(system);
    return new AkkaEngine(materializer);
  }
}
