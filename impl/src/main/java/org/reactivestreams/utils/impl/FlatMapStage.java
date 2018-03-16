package org.reactivestreams.utils.impl;

import org.reactivestreams.utils.spi.Graph;

import java.util.function.Function;

class FlatMapStage<T, R> extends GraphStage implements GraphLogic.InletListener, GraphLogic.OutletListener {
  private final GraphLogic.StageInlet<T> inlet;
  private final GraphLogic.StageOutlet<R> outlet;
  private final Function<T, Graph> mapper;

  private GraphLogic.SubStageInlet<R> substream;
  private Throwable error;

  FlatMapStage(GraphLogic graphLogic, GraphLogic.StageInlet<T> inlet, GraphLogic.StageOutlet<R> outlet, Function<T, Graph> mapper) {
    super(graphLogic);
    this.inlet = inlet;
    this.outlet = outlet;
    this.mapper = mapper;

    inlet.setListener(this);
    outlet.setListener(this);
  }

  @Override
  public void onPush() {
    Graph graph = mapper.apply(inlet.grab());
    substream = createSubInlet(graph);
    substream.setListener(new GraphLogic.InletListener() {
      @Override
      public void onPush() {
        outlet.push(substream.grab());
      }

      @Override
      public void onUpstreamFinish() {
        substream = null;
        if (inlet.isFinished()) {
          if (error != null) {
            outlet.fail(error);
          } else {
            outlet.finish();
          }
        } else if (outlet.isAvailable()) {
          inlet.pull();
        }
      }

      @Override
      public void onUpstreamFailure(Throwable error) {
        outlet.fail(error);
        if (!inlet.isFinished()) {
          inlet.finish();
        }
      }
    });
    substream.start();
    substream.pull();
  }

  @Override
  public void onUpstreamFinish() {
    if (substream == null) {
      outlet.finish();
    }
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    if (substream == null) {
      outlet.fail(error);
    } else {
      this.error = error;
    }
  }

  @Override
  public void onPull() {
    if (substream == null) {
      inlet.pull();
    } else {
      substream.pull();
    }
  }

  @Override
  public void onDownstreamFinish() {
    if (!inlet.isFinished()) {
      inlet.finish();
    }
    if (substream != null) {
      substream.finish();
    }
  }
}
