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

package com.lightbend.streams.akka;

import akka.stream.Materializer;
import akka.stream.javadsl.*;
import com.lightbend.streams.ReactiveStreamsEngine;
import com.lightbend.streams.SubscriberWithResult;
import com.lightbend.streams.spi.Stage;
import com.lightbend.streams.spi.UnsupportedStageException;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

/**
 * Akka implementation of the {@link ReactiveStreamsEngine}.
 */
public class AkkaEngine implements ReactiveStreamsEngine {

  private final Materializer materializer;

  public AkkaEngine(Materializer materializer) {
    this.materializer = materializer;
  }


  @Override
  public <T> Publisher<T> buildPublisher(Iterable<Stage> stages) throws UnsupportedStageException {
    Source source = null;
    Flow flow = Flow.create();
    for (Stage stage : stages) {
      if (source == null) {
        source = toSource(stage);
      } else {
        flow = applyStage(flow, stage);
      }
    }
    return (Publisher<T>) source
        .via(flow)
        .runWith(JavaFlowSupport.Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), materializer);
  }

  @Override
  public <T, R> SubscriberWithResult<T, R> buildSubscriber(Iterable<Stage> stages) throws UnsupportedStageException {
    Flow flow = Flow.create();
    for (Stage stage : stages) {
      if (stage.hasOutlet()) {
        flow = applyStage(flow, stage);
      } else {
        return (SubscriberWithResult<T, R>) JavaFlowSupport.Source.asSubscriber()
            .via(flow)
            .toMat(toSink(stage), (subscriber, result) ->
                new SubscriberWithResult((Subscriber) subscriber, (CompletionStage) result))
            .run(materializer);
      }
    }

    throw new IllegalStateException("Graph did not have terminal stage");
  }

  @Override
  public <T, R> Processor<T, R> buildProcessor(Iterable<Stage> stages) throws UnsupportedStageException {
    Flow flow = Flow.create();
    for (Stage stage : stages) {
      flow = applyStage(flow, stage);
    }
    return (Processor<T, R>) JavaFlowSupport.Flow.toProcessor(flow).run(materializer);
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Iterable<Stage> stages) throws UnsupportedStageException {
    Source source = null;
    Flow flow = Flow.create();
    for (Stage stage : stages) {
      if (source == null) {
        source = toSource(stage);
      } else if (stage.hasOutlet()) {
        flow = applyStage(flow, stage);
      } else {
        return (CompletionStage) source.via(flow).runWith(toSink(stage), materializer);
      }
    }

    throw new IllegalStateException("Graph did not have terminal stage");
  }

  private Flow applyStage(Flow flow, Stage stage) {
    if (stage instanceof Stage.Map) {
      Function<Object, Object> mapper = (Function) ((Stage.Map) stage).getMapper();
      return flow.map(mapper::apply);
    } else if (stage instanceof Stage.Filter) {
      Predicate<Object> predicate = (Predicate) (((Stage.Filter) stage).getPredicate());
      return flow.filter(predicate::test);
    } else if (stage instanceof Stage.Processor) {
      Processor<Object, Object> processor = (Processor) (((Stage.Processor) stage).getProcessor());
      Flow processorFlow;
      try {
        processorFlow = JavaFlowSupport.Flow.fromProcessor(() -> processor);
      } catch (Exception e) {
        // Technically can't happen, since the lambda we passed doesn't throw anything.
        throw new RuntimeException("Unexpected exception thrown", e);
      }
      return flow.via(processorFlow);
    } else if (stage.hasInlet() && stage.hasOutlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an inlet and an outlet.");
    }
  }

  private Sink toSink(Stage stage) {
    if (stage == Stage.FindFirst.INSTANCE) {
      return Sink.headOption();
    } else if (stage instanceof Stage.Collect) {
      Collector collector = ((Stage.Collect) stage).getCollector();
      BiConsumer accumulator = collector.accumulator();
      Sink<Object, CompletionStage<Object>> sink = Sink.fold(collector.supplier().get(), (resultContainer, in) -> {
        accumulator.accept(resultContainer, in);
        return resultContainer;
      });
      if (collector.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH)) {
        return sink;
      } else {
        return sink.mapMaterializedValue(result -> result.thenApply(collector.finisher()));
      }
    } else if (stage instanceof Stage.Subscriber) {
      return Flow.create()
          .watchTermination((left, done) -> done.thenApply(d -> null))
          .to((Sink) JavaFlowSupport.Sink.fromSubscriber(((Stage.Subscriber) stage).getSubscriber()));
    } else if (stage.hasInlet() && !stage.hasOutlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an inlet and no outlet.");
    }
  }

  private Source toSource(Stage stage) {
    if (stage instanceof Stage.OfMany) {
      return Source.from(((Stage.OfMany) stage).getElements());
    } else if (stage instanceof Stage.OfSingle) {
      return Source.single(((Stage.OfSingle) stage).getElement());
    } else if (stage instanceof Stage.Empty) {
      return Source.empty();
    } else if (stage instanceof Stage.Publisher) {
      return JavaFlowSupport.Source.fromPublisher(((Stage.Publisher) stage).getPublisher());
    } else if (stage instanceof Stage.Failed) {
      return Source.failed(((Stage.Failed) stage).getError());
    } else if (stage.hasOutlet() && !stage.hasInlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an outlet and no inlet.");
    }
  }
}
