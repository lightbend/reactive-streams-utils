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

import hu.akarnokd.rxjava2.interop.FlowInterop;
import hu.akarnokd.rxjava2.interop.FlowableInterop;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Flowable;
import org.reactivestreams.utils.ReactiveStreamsEngine;
import org.reactivestreams.utils.SubscriberWithResult;
import org.reactivestreams.utils.spi.Graph;
import org.reactivestreams.utils.spi.Stage;
import org.reactivestreams.utils.spi.UnsupportedStageException;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

public class RxJavaEngine implements ReactiveStreamsEngine {

  @Override
  public <T> Flow.Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
    return FlowInterop.toFlowPublisher(this.buildFlowable(graph));
  }

  private <T> Flowable<T> buildFlowable(Graph graph) throws UnsupportedStageException {
    Flowable flowable = null;
    for (Stage stage : graph.getStages()) {
      if (flowable == null) {
        flowable = toFlowable(stage);
      } else {
        flowable = applyStage(flowable, stage);
      }
    }
    return flowable;
  }

  @Override
  public <T, R> SubscriberWithResult<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
    Flow.Processor processor = new BridgedProcessor();

    Flowable flowable = FlowInterop.fromFlowProcessor(processor);
    for (Stage stage : graph.getStages()) {
      if (stage.hasOutlet()) {
        flowable = applyStage(flowable, stage);
      } else {
        CompletionStage result = applySubscriber(flowable, stage);

        return new SubscriberWithResult(processor, result);
      }
    }

    throw new IllegalStateException("Graph did not have terminal stage");
  }

  @Override
  public <T, R> Flow.Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
    Flow.Processor processor = new BridgedProcessor();

    Flowable flowable = FlowInterop.fromFlowProcessor(processor);
    for (Stage stage : graph.getStages()) {
      flowable = applyStage(flowable, stage);
    }

    return new WrappedProcessor<>(processor, FlowInterop.toFlowPublisher(flowable));
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
    Flowable flowable = null;
    for (Stage stage : graph.getStages()) {
      if (flowable == null) {
        flowable = toFlowable(stage);
      } else if (stage.hasOutlet()) {
        flowable = applyStage(flowable, stage);
      } else {
        return applySubscriber(flowable, stage);
      }
    }

    throw new IllegalStateException("Graph did not have terminal stage");
  }

  private Flowable applyStage(Flowable flowable, Stage stage) {
    if (stage instanceof Stage.Map) {
      Function<Object, Object> mapper = (Function) ((Stage.Map) stage).getMapper();
      return flowable.map(mapper::apply);
    } else if (stage instanceof Stage.Filter) {
      Predicate<Object> predicate = (Predicate) (((Stage.Filter) stage).getPredicate()).get();
      return flowable.filter(predicate::test);
    } else if (stage instanceof Stage.TakeWhile) {
      Predicate<Object> predicate = (Predicate) (((Stage.TakeWhile) stage).getPredicate()).get();
      boolean inclusive = ((Stage.TakeWhile) stage).isInclusive();
      if (inclusive) {
        return flowable.takeUntil(element -> !predicate.test(element));
      } else {
        return flowable.takeWhile(predicate::test);
      }
    } else if (stage instanceof Stage.FlatMap) {
      Function<Object, Graph> mapper = (Function) ((Stage.FlatMap) stage).getMapper();
      return flowable.concatMap(e -> buildFlowable(mapper.apply(e)));
    } else if (stage instanceof Stage.FlatMapCompletionStage) {
      Function<Object, CompletionStage<Object>> mapper = (Function) ((Stage.FlatMapCompletionStage) stage).getMapper();
      return flowable.concatMap(e -> SingleInterop.fromFuture(mapper.apply(e)).toFlowable(), 1);
    } else if (stage instanceof Stage.FlatMapIterable) {
      Function<Object, Iterable<Object>> mapper = (Function) ((Stage.FlatMapIterable) stage).getMapper();
      return flowable.concatMapIterable(mapper::apply);
    } else if (stage instanceof Stage.Processor) {
      Flow.Processor<Object, Object> processor = (Flow.Processor) (((Stage.Processor) stage).getProcessor());
      flowable.subscribe(new FlowSubscriberAdapter(processor));
      FlowInterop.fromFlowPublisher(processor);
      return FlowInterop.fromFlowPublisher(processor);
    } else if (stage.hasInlet() && stage.hasOutlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an inlet and an outlet.");
    }
  }

  private CompletionStage applySubscriber(Flowable flowable, Stage stage) {
    if (stage == Stage.FindFirst.INSTANCE) {
      try {
        return SingleInterop.get().apply(flowable.map(Optional::of).first(Optional.empty()));
      } catch (Exception e) {
        // Shouldn't happen
        throw new RuntimeException("Unexpected error", e);
      }
    } else if (stage instanceof Stage.CollectProcessor) {
      Collector collector = ((Stage.CollectProcessor) stage).getCollector();
      try {
        return FlowableInterop.first().apply(flowable.compose(FlowableInterop.collect(collector)));
      } catch (Exception e) {
        // Shouldn't happen
        throw new RuntimeException("Unexpected error", e);
      }
    } else if (stage == Stage.Cancel.INSTANCE) {
      flowable.subscribe().dispose();
      return CompletableFuture.completedFuture(null);
    } if (stage instanceof Stage.Subscriber) {
      Flow.Subscriber subscriber = ((Stage.Subscriber) stage).getSubscriber();
      TerminationWatchingSubscriber watchTermination = new TerminationWatchingSubscriber(subscriber);
      flowable.subscribe(new FlowSubscriberAdapter(watchTermination));
      return watchTermination.getTermination();
    } else if (stage.hasInlet() && !stage.hasOutlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an inlet and no outlet.");
    }
  }

  private Flowable toFlowable(Stage stage) {
    if (stage instanceof Stage.Of) {
      return Flowable.fromIterable(((Stage.Of) stage).getElements());
    } else if (stage instanceof Stage.Publisher) {
      return FlowInterop.fromFlowPublisher(((Stage.Publisher) stage).getPublisher());
    } else if (stage instanceof Stage.Concat) {
      Graph first = ((Stage.Concat) stage).getFirst();
      Graph second = ((Stage.Concat) stage).getSecond();
      CancelInjectingPublisher secondPublisher = new CancelInjectingPublisher(buildFlowable(second));
      return Flowable.concat(buildFlowable(first), secondPublisher)
          .doOnTerminate(secondPublisher::cancelIfNotSubscribed)
          .doOnCancel(secondPublisher::cancelIfNotSubscribed);
    } else if (stage instanceof Stage.Failed) {
      return Flowable.error(((Stage.Failed) stage).getError());
    } else if (stage.hasOutlet() && !stage.hasInlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an outlet and no inlet.");
    }
  }

  private static final Object UNIT = new Object();
}
