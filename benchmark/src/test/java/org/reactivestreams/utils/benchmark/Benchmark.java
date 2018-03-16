package org.reactivestreams.utils.benchmark;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.lightbend.reactivestreams.rxjava.RxJavaEngine;
import com.lightbend.reactivestreams.utils.AkkaEngine;
import org.reactivestreams.utils.ReactiveStreams;
import org.reactivestreams.utils.ReactiveStreamsEngine;
import org.reactivestreams.utils.impl.ReactiveStreamsEngineImpl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

public class Benchmark {
  public static void main(String... args) {
    Runner refImpl = new Runner(new ReactiveStreamsEngineImpl(), "RefImpl");
    warmupAndRun(refImpl);

    ActorSystem system = ActorSystem.create();
    ActorMaterializer mat = ActorMaterializer.create(system);
    Runner akka = new Runner(new AkkaEngine(mat), "Akka");
    try {
      warmupAndRun(akka);
    } finally {
      system.terminate();
    }

    Runner rxjava = new Runner(new RxJavaEngine(), "RxJava");
    warmupAndRun(rxjava);

    Function<Function<Runner, Long>, long[]> results = f ->
        new long[] {f.apply(refImpl), f.apply(akka), f.apply(rxjava)};

    System.out.println(String.format(" %-35s | %-7s | %-7s | %-7s |", "Engine", refImpl.name, akka.name, rxjava.name));
    System.out.println("--------------------------------------------------------------------");
    printResult("identity processor with long stream", results.apply(r -> r.identityProcessorLongStreamTime));
    printResult("complex processor with long stream", results.apply(r -> r.complexProcessorLongStreamTime));
    printResult("serial materialization", results.apply(r -> r.oneAtATimeMaterializationTime));
    printResult("concurrent materialization", results.apply(r -> r.concurrentMaterializationTime));
    printResult("long concat map stream", results.apply(r -> r.subStreamTime));
  }

  private static void printResult(String name, long... results) {
    System.out.print(String.format(" %-35s |",  name));
    for (long result: results) {
      System.out.print(String.format(" %7d |", result));
    }
    System.out.println();
  }

  private static void warmupAndRun(Runner runner) {
    System.out.println(runner.name + " warmup:");
    runner.run();
    System.out.println();

    System.out.println(runner.name + " actual run:");
    runner.run();
    System.out.println();
  }

  private static class Runner {
    final ReactiveStreamsEngine engine;
    final String name;

    long identityProcessorLongStreamTime;
    long complexProcessorLongStreamTime;
    long oneAtATimeMaterializationTime;
    long concurrentMaterializationTime;
    long subStreamTime;

    public Runner(ReactiveStreamsEngine engine, String name) {
      this.engine = engine;
      this.name = name;
    }

    public void run() {

      System.out.print(name + " identity processor time.... ");
      identityProcessorLongStreamTime = time(() -> {
        int iterations = 100000000;
        AtomicInteger count = new AtomicInteger();
        await(ReactiveStreams.fromIterable(() -> IntStream.range(1, iterations).iterator())
            .forEach(i -> count.incrementAndGet()).build(engine));
        assert count.get() == iterations;
      });
      System.out.println(identityProcessorLongStreamTime);

      System.out.print(name + " complex processor time.... ");
      complexProcessorLongStreamTime = time(() -> {
        int iterations = 10000000;
        AtomicInteger count = new AtomicInteger();
        await(ReactiveStreams.fromIterable(() -> IntStream.range(1, iterations).iterator())
            .map(Function.identity())
            .map(Function.identity())
            .filter(i -> i % 2 == 1)
            .map(Function.identity())
            .map(Function.identity())
            .forEach(i -> count.incrementAndGet()).build(engine));
        assert count.get() == iterations / 2;
      });
      System.out.println(complexProcessorLongStreamTime);

      System.out.print(name + " one at a time materialization... ");
      oneAtATimeMaterializationTime = time(() -> {
        int iterations = 100000;
        AtomicInteger count = new AtomicInteger();
        Runnable[] runnable = new Runnable[1];
        CompletableFuture<Void> done = new CompletableFuture<>();
        runnable[0] = () -> {
          if (count.getAndIncrement() < iterations) {
            ReactiveStreams.of(1, 2, 3).forEach(e -> {}).build(engine)
                .thenRunAsync(runnable[0]);
          } else {
            done.complete(null);
          }
        };
        runnable[0].run();

        await(done);
      });
      System.out.println(oneAtATimeMaterializationTime);

      System.out.print(name + " concurrent materialization... ");
      concurrentMaterializationTime = time(() -> {
        int iterations = 100000;
        int concurrency = 8;
        Runnable[] runnable = new Runnable[concurrency];
        CompletableFuture<Void>[] dones = new CompletableFuture[concurrency];
        for (int i = 0; i < concurrency; i++) {
          // effectively final...
          int j = i;
          AtomicInteger count = new AtomicInteger();
          dones[j] = new CompletableFuture<>();
          runnable[j] = () -> {
            if (count.getAndIncrement() < iterations) {
              ReactiveStreams.of(1, 2, 3).forEach(e -> {}).build(engine)
                  .thenRunAsync(runnable[j]);
            } else {
              dones[j].complete(null);
            }
          };
          runnable[j].run();
        }
        for (CompletableFuture<Void> done: dones) {
          await(done);
        }
      });
      System.out.println(concurrentMaterializationTime);

      System.out.print(name + " sub stream time.... ");
      subStreamTime = time(() -> {
        int iterations = 100000;
        AtomicInteger count = new AtomicInteger();
        await(ReactiveStreams.fromIterable(() -> IntStream.range(1, iterations).iterator())
            .flatMap(i -> ReactiveStreams.of(1, 2, 3, 4))
            .forEach(i -> count.incrementAndGet()).build(engine));
        assert count.get() == iterations * 4;
      });
      System.out.println(subStreamTime);
    }
  }

  private static <T> T await(CompletionStage<T> future) {
    try {
      return future.toCompletableFuture().get(1, TimeUnit.MINUTES);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static long time(Runnable operation) {
    System.gc();
    long start = System.currentTimeMillis();
    operation.run();
    return System.currentTimeMillis() - start;
  }
}
