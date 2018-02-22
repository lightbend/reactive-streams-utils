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

package com.lightbend.streams.tck;

import com.lightbend.streams.ReactiveStreamsEngine;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Factory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

/**
 * The Reactive Streams TCK.
 *
 * A concrete class that extends this class is all that is needed to verify a {@link ReactiveStreamsEngine} against
 * this TCK.
 *
 * It produces a number of TestNG test classes via the TestNG {@link Factory} annotated {@link #allTests()} method.
 *
 * @param <E> The type of the Reactive Streams engine.
 */
public abstract class ReactiveStreamsTck<E extends ReactiveStreamsEngine> {

  private final TestEnvironment testEnvironment;

  public ReactiveStreamsTck(TestEnvironment testEnvironment) {
    this.testEnvironment = testEnvironment;
  }

  /**
   * Override to provide the reactive streams engine.
   */
  protected abstract E createEngine();

  /**
   * Override to implement custom shutdown logic for the Reactive Streams engine.
   */
  protected void shutdownEngine(E engine) {
    // By default, do nothing.
  }

  private E engine;

  @AfterSuite(alwaysRun = true)
  public void shutdownEngine() {
    if (engine != null) {
      shutdownEngine(engine);
    }
  }

  @Factory
  public Object[] allTests() {
    engine = createEngine();

    List<Function<VerificationDeps, AbstractStageVerification>> stageVerifications = Arrays.asList(
        OfSingleStageVerification::new,
        OfManyStageVerification::new,
        EmptyStageVerification::new,
        MapStageVerification::new,
        FilterStageVerification::new,
        FindFirstStageVerification::new,
        CollectStageVerification::new
    );

    List<Object> allTests = new ArrayList<>();
    VerificationDeps deps = new VerificationDeps();
    for (Function<VerificationDeps, AbstractStageVerification> creator: stageVerifications) {
      AbstractStageVerification stageVerification = creator.apply(deps);
      allTests.add(stageVerification);
      allTests.addAll(stageVerification.reactiveStreamsTckVerifiers());
    }

    return allTests.toArray();
  }

  class VerificationDeps {
    ReactiveStreamsEngine engine() {
      return engine;
    }
    TestEnvironment testEnvironment() {
      return testEnvironment;
    }
    ExecutorService executorService() {
      return ForkJoinPool.commonPool();
    }
  }

}
