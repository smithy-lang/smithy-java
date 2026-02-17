/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.example.eventstreaming;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.rpcv2.RpcV2CborProtocol;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.core.serde.event.EventStreamWriter;
import software.amazon.smithy.java.example.eventstreaming.client.FizzBuzzServiceClient;
import software.amazon.smithy.java.example.eventstreaming.model.FizzBuzzInput;
import software.amazon.smithy.java.example.eventstreaming.model.FizzBuzzOutput;
import software.amazon.smithy.java.example.eventstreaming.model.FizzBuzzStream;
import software.amazon.smithy.java.example.eventstreaming.model.Value;
import software.amazon.smithy.java.example.eventstreaming.model.ValueStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// TODO: Update the test to create and run the server in setup before the test
@Disabled("This test requires manually running a server locally and then verifies client behavior against it.")
public class EventStreamTest {

    @ParameterizedTest
    @MethodSource("clients")
    public void fizzBuzz(FizzBuzzServiceClient client) {
        int range = 100;
        FizzBuzzInput input = FizzBuzzInput.builder()
                .stream(EventStream.newWriter())
                .build();
        Thread.ofVirtual().start(() -> {
            try (EventStreamWriter<ValueStream> writer = input.getStream().asWriter()) {
                int count = 0;
                while (count++ < range) {
                    ValueStream value = ValueStream.builder()
                            .value(Value.builder().value(count).build())
                            .build();
                    writer.write(value);
                }
            }
        });
        FizzBuzzOutput output = client.fizzBuzz(input);
        System.out.println("Initial messages done");

        AtomicLong receivedEvents = new AtomicLong();
        Set<Long> unbuzzed = new HashSet<>();
        output.getStream().asReader().forEach(item -> {
            receivedEvents.incrementAndGet();
            long value;
            switch (item) {
                case FizzBuzzStream.FizzMember(var fizz):
                    value = fizz.getValue();
                    System.out.println("received fizz: " + value);
                    assertEquals(0, value % 3);
                    if (value % 5 == 0) {
                        assertTrue(unbuzzed.add(value), "Fizz already received for " + value);
                    }
                    break;
                case FizzBuzzStream.BuzzMember(var buzz):
                    value = buzz.getValue();
                    System.out.println("received buzz: " + value);
                    assertEquals(0, value % 5);
                    if (value % 3 == 0) {
                        assertTrue(unbuzzed.remove(value), "No fizz for " + value);
                    }
                    break;
                default:
                    fail("Unexpected event: " + item.getClass());
                    break;
            }
        });
        assertTrue(unbuzzed.isEmpty(), unbuzzed.size() + " unbuzzed fizzes");
        assertEquals((range / 3) + (range / 5), receivedEvents.get());
    }

    public static List<FizzBuzzServiceClient> clients() {
        return List.of(
                /*
                FizzBuzzServiceClient.builder()
                        .endpointResolver(EndpointResolver.staticHost("http://localhost:8080"))
                        .build(),

                 */
                FizzBuzzServiceClient.builder()
                        .protocol(new RpcV2CborProtocol.Factory()
                                .createProtocol(ProtocolSettings.builder()
                                        .service(ShapeId.from("smithy.example#TickService"))
                                        .build(), Rpcv2CborTrait.builder().build()))
                        .endpointResolver(EndpointResolver.staticHost("http://localhost:8000"))
                        .build()

        );
    }
}
