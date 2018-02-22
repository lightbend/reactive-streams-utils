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

package org.reactivestreams.utils.examples;

import org.reactivestreams.utils.ReactiveStreams;
import org.reactivestreams.utils.SubscriberWithResult;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Examples {

  public void closedGraph() {

    // First, a trivial example. This is just to show the fluency of the API.
    // It would make no sense to do this in practice, since the JDK8 streams API itself
    // will do the below operations much more optimally.
    CompletionStage<Optional<Integer>> result = ReactiveStreams
        .fromIterable(() -> IntStream.range(1, 1000).boxed().iterator())
        .filter(i -> (i & 1) == 1)
        .map(i -> i * 2)
        .collect(Collectors.reducing((i, j) -> i + j))
        .build();
  }

  public void providePublisher() {

    // Let's say we need to provide a Publisher<ByteBuffer>, perhaps we want to transform
    // a list of objects to CSV.
    List<MyDomainObject> domainObjects = new ArrayList<>();

    Flow.Publisher<ByteBuffer> publisher = ReactiveStreams
        .fromIterable(domainObjects)
        .map(obj -> String.format("%s,%s\n", obj.getField1(), obj.getField2()))
        .map(line -> ByteBuffer.wrap(line.getBytes()))
        .build();

  }

  public void provideSubscriber() {

    // Let's say we need to provide a subscriber (eg, like the JDK9 HttpClient requires when
    // consuming response bodies), and we want to parse them using a processor provided by
    // another library into domain objects.
    Flow.Processor<ByteBuffer, MyDomainObject> parser = createParser();

    SubscriberWithResult<ByteBuffer, List<MyDomainObject>> sr =
      ReactiveStreams.<ByteBuffer>builder()
        .via(parser)
        .toList()
        .build();

    Flow.Subscriber<ByteBuffer> subscriber = sr.getSubscriber();
    CompletionStage<List<MyDomainObject>> result = sr.getResult();

  }

  public void provideProcessor() {

    // Let's say a messaging library requires us to process messages, and then emit
    // the message identifier for each message successfully processed, so that it
    // can consider the message processed (and commit it).
    Flow.Processor<Message<MyDomainObject>, MessageIdentifier> processor =
        ReactiveStreams.<Message<MyDomainObject>>builder()
          .map(message -> {
            handleObject(message.getMessage());
            return message.getIdentifier();
          })
          .build();
  }

  public void consumePublisher() {

    // Let's say a library gives us a publisher, which we want to parse as MyDomainObject.
    Flow.Publisher<ByteBuffer> bytesPublisher = makeRequest();

    Flow.Processor<ByteBuffer, MyDomainObject> parser = createParser();

    CompletionStage<List<MyDomainObject>> result = ReactiveStreams
        .fromPublisher(bytesPublisher)
        .via(parser)
        .toList()
        .build();

  }

  public void feedSubscriber() {

    // Let's say some library has given us a subscriber that we have to feed.
    List<MyDomainObject> domainObjects = new ArrayList<>();

    Flow.Subscriber<ByteBuffer> subscriber = createSubscriber();

    CompletionStage<Void> completion = ReactiveStreams
        .fromIterable(domainObjects)
        .map(obj -> String.format("%s,%s\n", obj.getField1(), obj.getField2()))
        .map(line -> ByteBuffer.wrap(line.getBytes()))
        .to(subscriber)
        .build();

  }

  Flow.Publisher<ByteBuffer> makeRequest() {
    return ReactiveStreams.<ByteBuffer>empty().build();
  }

  Flow.Subscriber<ByteBuffer> createSubscriber() {
    return ReactiveStreams.<ByteBuffer>builder().findFirst().build().getSubscriber();
  }

  void handleObject(MyDomainObject obj) {}

  class Message<T> {
    MessageIdentifier getIdentifier() {
      return new MessageIdentifier();
    }
    T getMessage() {
      return null;
    }
  }

  class MessageIdentifier {}

  Flow.Processor<ByteBuffer, MyDomainObject> createParser() {
    return ReactiveStreams.<ByteBuffer>builder().map(bytes -> new MyDomainObject()).build();
  }

  class MyDomainObject {
    String getField1() { return "field1"; }
    String getField2() { return "field2"; }
  }
}
