/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.examples.eventstreaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.examples.eventstreaming.client.FizzBuzzServiceClient;
import software.amazon.smithy.java.examples.eventstreaming.model.FizzBuzzInput;
import software.amazon.smithy.java.examples.eventstreaming.model.FizzBuzzOutput;
import software.amazon.smithy.java.examples.eventstreaming.model.FizzBuzzStream;
import software.amazon.smithy.java.examples.eventstreaming.model.Value;
import software.amazon.smithy.java.examples.eventstreaming.model.ValueStream;

// This test requires manually running a server locally and then verifies client behavior against it.
public class EventStreamTest {

    @Test
    public void fizzBuzz() throws InterruptedException {
        var client = FizzBuzzServiceClient.builder()
                .endpointResolver(EndpointResolver.staticHost("http://localhost:8080"))
                .build();

        int range = 100;

        FizzBuzzInput input = FizzBuzzInput.builder()
                .stream(new ValueStreamPublisher(range))
                .build();
        FizzBuzzOutput output = client.fizzBuzz(input);

        System.out.println("Initial messages done");

        AtomicLong receivedEvents = new AtomicLong();
        Set<Long> unbuzzed = new HashSet<>();
        output.stream().subscribe(new Flow.Subscriber<>() {

            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1L);
            }

            @Override
            public void onNext(FizzBuzzStream item) {
                receivedEvents.incrementAndGet();
                long value;
                switch (item.type()) {
                    case fizz:
                        value = item.getValue();
                        System.out.println("received fizz: " + value);
                        assertEquals(0, value % 3);
                        if (value % 5 == 0) {
                            assertTrue(unbuzzed.add(value), "Fizz already received for " + value);
                        }
                        break;
                    case buzz:
                        value = item.getValue();
                        System.out.println("received buzz: " + value);
                        assertEquals(0, value % 5);
                        if (value % 3 == 0) {
                            assertTrue(unbuzzed.remove(value), "No fizz for " + value);
                        }
                        break;
                    default:
                        fail("Unexpected event: " + item.type());
                        break;
                }
                subscription.request(1L);
            }

            @Override
            public void onError(Throwable throwable) {}

            @Override
            public void onComplete() {
                System.out.println("output stream completed");
            }
        });

        // wait to receive events in the response stream
        Thread.sleep(100);

        assertTrue(unbuzzed.isEmpty(), unbuzzed.size() + " unbuzzed fizzes");
        assertEquals((range / 3) + (range / 5), receivedEvents.get());
    }

    private static class ValueStreamPublisher implements Flow.Publisher<ValueStream> {
        private final int range;

        public ValueStreamPublisher(int range) {
            this.range = range;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ValueStream> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {

                int count = 0;

                @Override
                public void request(long n) {
                    // sleeping between sending request events so there's chance to process response events in parallel
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (count++ < range) {
                        ValueStream value = ValueStream.builder()
                                .value(Value.builder().value(count).build())
                                .build();
                        System.out.println("sent: " + value);
                        subscriber.onNext(value);
                    } else {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    subscriber.onComplete();
                }
            });
        }
    }
}
