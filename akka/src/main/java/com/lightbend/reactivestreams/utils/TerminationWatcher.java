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

package com.lightbend.reactivestreams.utils;

import akka.stream.*;
import akka.stream.stage.*;
import scala.Tuple2;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class TerminationWatcher<T> extends GraphStageWithMaterializedValue<FlowShape<T, T>, CompletionStage<Void>> {
  private final Inlet<T> in = Inlet.create("TerminationWatcher.in");
  private final Outlet<T> out = Outlet.create("TerminationWatcher.out");

  private final FlowShape<T, T> shape = FlowShape.of(in, out);

  @Override
  public FlowShape<T, T> shape() {
    return shape;
  }

  @Override
  public Tuple2<GraphStageLogic, CompletionStage<Void>> createLogicAndMaterializedValue(Attributes inheritedAttributes) {
    CompletableFuture<Void> completion = new CompletableFuture<>();
    GraphStageLogic logic = new GraphStageLogic(shape()) {
      {
        setHandler(in, new AbstractInHandler() {
          @Override
          public void onPush() throws Exception {
            push(out, grab(in));
          }

          @Override
          public void onUpstreamFinish() throws Exception {
            complete(out);
            completion.complete(null);
          }

          @Override
          public void onUpstreamFailure(Throwable ex) throws Exception {
            fail(out, ex);
            completion.completeExceptionally(ex);
          }
        });
        setHandler(out, new AbstractOutHandler() {
          @Override
          public void onPull() throws Exception {
            pull(in);
          }

          @Override
          public void onDownstreamFinish() throws Exception {
            cancel(in);
            completion.completeExceptionally(new CancellationException("cancelled"));
          }
        });
      }

    };
    return new Tuple2<>(logic, completion);
  }
}