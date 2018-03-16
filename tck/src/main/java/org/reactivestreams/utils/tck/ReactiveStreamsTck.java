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

package org.reactivestreams.utils.tck;

import org.reactivestreams.utils.ReactiveStreamsEngine;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Factory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

/**
 * The Reactive Streams TCK.
 * <p>
 * A concrete class that extends this class is all that is needed to verify a {@link ReactiveStreamsEngine} against
 * this TCK.
 * <p>
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

  /**
   * Override this to disable/enable tests, useful for debugging one test at a time.
   */
  protected boolean isEnabled(Object test) {
    return true;
  }

  private E engine;
  private ScheduledExecutorService executorService;

  @AfterSuite(alwaysRun = true)
  public void shutdownEngine() {
    if (engine != null) {
      shutdownEngine(engine);
    }
  }

  @Factory
  public Object[] allTests() {
    engine = createEngine();
    executorService = Executors.newScheduledThreadPool(4);

    List<Function<VerificationDeps, AbstractStageVerification>> stageVerifications = Arrays.asList(
        OfStageVerification::new,
        MapStageVerification::new,
        FlatMapStageVerification::new,
        FilterStageVerification::new,
        FindFirstStageVerification::new,
        CollectStageVerification::new,
        TakeWhileStageVerification::new,
        FlatMapCompletionStageVerification::new,
        FlatMapIterableStageVerification::new,
        ForEachStageVerification::new,
        ConcatStageVerification::new,
        EmptyProcessorVerification::new
    );

    List<Object> allTests = new ArrayList<>();
    VerificationDeps deps = new VerificationDeps();
    for (Function<VerificationDeps, AbstractStageVerification> creator : stageVerifications) {
      AbstractStageVerification stageVerification = creator.apply(deps);
      allTests.add(stageVerification);
      allTests.addAll(stageVerification.reactiveStreamsTckVerifiers());
    }

    return allTests.stream().filter(this::isEnabled).toArray();
  }

  class VerificationDeps {
    ReactiveStreamsEngine engine() {
      return engine;
    }

    TestEnvironment testEnvironment() {
      return testEnvironment;
    }

    ScheduledExecutorService executorService() {
      return executorService;
    }
  }

}
