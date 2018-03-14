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

package com.lightbend.reactivestreams.utils;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.*;
import org.reactivestreams.utils.ReactiveStreamsEngine;
import org.reactivestreams.utils.SubscriberWithResult;
import org.reactivestreams.utils.spi.Graph;
import org.reactivestreams.utils.spi.Stage;
import org.reactivestreams.utils.spi.UnsupportedStageException;

import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
  public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
    // Optimization - if it's just a publisher, return it directly
    Stage firstStage = graph.getStages().iterator().next();
    if (graph.getStages().size() == 1 && firstStage instanceof Stage.Publisher) {
      return (Publisher) ((Stage.Publisher) firstStage).getPublisher();
    }

    return this.<T>buildSource(graph)
        .runWith(JavaFlowSupport.Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), materializer);
  }

  private <T> Source<T, NotUsed> buildSource(Graph graph) throws UnsupportedStageException {
    Source source = null;
    Flow flow = Flow.create();
    for (Stage stage : graph.getStages()) {
      if (source == null) {
        source = toSource(stage);
      } else {
        flow = applyStage(flow, stage);
      }
    }
    return (Source) source
        .via(flow);
  }

  @Override
  public <T, R> SubscriberWithResult<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
    Flow flow = Flow.create();
    for (Stage stage : graph.getStages()) {
      if (stage.hasOutlet()) {
        flow = applyStage(flow, stage);
      } else {
        return (SubscriberWithResult) JavaFlowSupport.Source.asSubscriber()
            .via(flow)
            .toMat(toSink(stage), (subscriber, result) ->
                new SubscriberWithResult((Subscriber) subscriber, (CompletionStage) result))
            .run(materializer);
      }
    }

    throw new IllegalStateException("Graph did not have terminal stage");
  }

  @Override
  public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
    // Optimization - if it's just a processor, return it directly
    Stage firstStage = graph.getStages().iterator().next();
    if (graph.getStages().size() == 1 && firstStage instanceof Stage.Processor) {
      return (Processor) ((Stage.Processor) firstStage).getProcessor();
    }

    Flow flow = Flow.create();
    for (Stage stage : graph.getStages()) {
      flow = applyStage(flow, stage);
    }
    return (Processor) JavaFlowSupport.Flow.toProcessor(flow).run(materializer);
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
    Source source = null;
    Flow flow = Flow.create();
    for (Stage stage : graph.getStages()) {
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
      Predicate<Object> predicate = (Predicate) (((Stage.Filter) stage).getPredicate()).get();
      return flow.filter(predicate::test);
    } else if (stage instanceof Stage.FlatMap) {
      Function<Object, Graph> mapper = (Function) ((Stage.FlatMap) stage).getMapper();
      return flow.flatMapConcat(e -> buildSource(mapper.apply(e)));
    } else if (stage instanceof Stage.TakeWhile) {
      Predicate<Object> predicate = (Predicate) (((Stage.TakeWhile) stage).getPredicate()).get();
      boolean inclusive = ((Stage.TakeWhile) stage).isInclusive();
      return flow.takeWhile(predicate::test, inclusive);
    } else if (stage instanceof Stage.FlatMapCompletionStage) {
      Function<Object, CompletionStage<Object>> mapper = (Function) ((Stage.FlatMapCompletionStage) stage).getMapper();
      return flow.mapAsync(1, mapper::apply);
    } else if (stage instanceof Stage.FlatMapIterable) {
      Function<Object, Iterable<Object>> mapper = (Function) ((Stage.FlatMapIterable) stage).getMapper();
      return flow.mapConcat(mapper::apply);
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
    } else if (stage instanceof Stage.ForEach) {
      Consumer action = ((Stage.ForEach) stage).getAction();
      return Sink.foreach(action::accept)
          .mapMaterializedValue(done -> done.thenApply(d -> null));
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
    if (stage instanceof Stage.Of) {
      Iterable elements = ((Stage.Of) stage).getElements();
      // perhaps a premature optimization?
      if (elements instanceof Collection) {
        int size = ((Collection) elements).size();
        if (size == 0) {
          return Source.empty();
        } else if (size == 1) {
          return Source.single(elements.iterator().next());
        }
      }
      return Source.from(elements);
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
