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

package com.lightbend.reactivestreams.rxjava;

import hu.akarnokd.rxjava2.interop.FlowInterop;
import hu.akarnokd.rxjava2.interop.FlowableInterop;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Flowable;
import org.reactivestreams.utils.ReactiveStreamsEngine;
import org.reactivestreams.utils.SubscriberWithResult;
import org.reactivestreams.utils.spi.Stage;
import org.reactivestreams.utils.spi.UnsupportedStageException;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

public class RxJavaEngine implements ReactiveStreamsEngine {

  @Override
  public <T> Flow.Publisher<T> buildPublisher(Iterable<Stage> stages) throws UnsupportedStageException {
    Flowable flowable = null;
    for (Stage stage : stages) {
      if (flowable == null) {
        flowable = toFlowable(stage);
      } else {
        flowable = applyStage(flowable, stage);
      }
    }
    return FlowInterop.toFlowPublisher(flowable);
  }

  @Override
  public <T, R> SubscriberWithResult<T, R> buildSubscriber(Iterable<Stage> stages) throws UnsupportedStageException {
    Flow.Processor processor = new BridgedProcessor();

    Flowable flowable = FlowInterop.fromFlowProcessor(processor);
    for (Stage stage : stages) {
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
  public <T, R> Flow.Processor<T, R> buildProcessor(Iterable<Stage> stages) throws UnsupportedStageException {
    Flow.Processor processor = new BridgedProcessor();

    Flowable flowable = FlowInterop.fromFlowProcessor(processor);
    for (Stage stage : stages) {
      flowable = applyStage(flowable, stage);
    }

    return new WrappedProcessor<>(processor, FlowInterop.toFlowPublisher(flowable));
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Iterable<Stage> stages) throws UnsupportedStageException {
    Flowable flowable = null;
    for (Stage stage : stages) {
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
      Predicate<Object> predicate = (Predicate) (((Stage.Filter) stage).getPredicate());
      return flowable.filter(predicate::test);
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
    } else if (stage instanceof Stage.Collect) {
      Collector collector = ((Stage.Collect) stage).getCollector();
      try {
        return FlowableInterop.first().apply(flowable.compose(FlowableInterop.collect(collector)));
      } catch (Exception e) {
        // Shouldn't happen
        throw new RuntimeException("Unexpected error", e);
      }
    } else if (stage instanceof Stage.Subscriber) {
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
    if (stage instanceof Stage.OfMany) {
      return Flowable.fromIterable(((Stage.OfMany) stage).getElements());
    } else if (stage instanceof Stage.OfSingle) {
      return Flowable.just(((Stage.OfSingle) stage).getElement());
    } else if (stage instanceof Stage.Empty) {
      return Flowable.empty();
    } else if (stage instanceof Stage.Publisher) {
      return FlowInterop.fromFlowPublisher(((Stage.Publisher) stage).getPublisher());
    } else if (stage instanceof Stage.Failed) {
      return Flowable.error(((Stage.Failed) stage).getError());
    } else if (stage.hasOutlet() && !stage.hasInlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an outlet and no inlet.");
    }
  }
}
