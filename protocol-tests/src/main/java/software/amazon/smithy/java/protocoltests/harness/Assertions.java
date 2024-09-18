/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpMessage;
import software.amazon.smithy.model.node.Node;

/**
 * Provides a number of testing utilities for validating protocol test results.
 */
final class Assertions {
    private Assertions() {}

    private static final Set<Character> HEADER_DELIMS = Set.of(
        '"',
        '(',
        ')',
        ',',
        '/',
        ':',
        ';',
        '<',
        '=',
        '>',
        '?',
        '@',
        '[',
        '\\',
        ']',
        '{',
        '}'
    );

    static void assertUriEquals(URI uri, String expected) {
        assertEquals(expected, uri.getRawPath());
    }

    static void assertHostEquals(SmithyHttpMessage message, String expected) {
        var hostValue = message.headers().allValues("Host");
        assertEquals(hostValue.size(), 1);
        assertEquals(hostValue.get(0), expected);
    }

    static void assertHeadersEqual(SmithyHttpMessage message, Map<String, String> expected) {
        for (var headerEntry : expected.entrySet()) {
            var headerValues = message.headers().allValues(headerEntry.getKey());
            assertNotNull(headerValues);
            var converted = convertHeaderToString(headerEntry.getKey(), headerValues);
            assertEquals(headerEntry.getValue(), converted);
        }
    }

    private static String convertHeaderToString(String key, List<String> values) {
        // TODO: Is this needed for all protocol tests?
        if (!key.equalsIgnoreCase("x-stringlist")) {
            return String.join(", ", values);
        }
        return values.stream().map(value -> {
            if (value.chars()
                .anyMatch(c -> HEADER_DELIMS.contains((char) c) || Character.isWhitespace((char) c))) {
                return '"' + value.replaceAll("[\\s\"]", "\\\\$0") + '"';
            }
            return value;
        }).collect(Collectors.joining(", "));
    }

    static void assertJsonBodyEquals(SmithyHttpMessage message, String jsonBody) {
        var body = new StringBuildingSubscriber(message.body()).getResult();

        // TODO: This should be "", not "{}". Issue is in json codec.
        String expected = "{}";
        if (!jsonBody.isEmpty()) {
            // Use the node parser to strip out white space.
            expected = Node.printJson(Node.parse(jsonBody));
        }
        assertEquals(expected, body);
    }

    /**
     * Subscriber that extracts the message body as a string
     */
    private static final class StringBuildingSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final StringBuilder builder = new StringBuilder();;
        private final CompletableFuture<String> result = new CompletableFuture<>();

        private StringBuildingSubscriber(Flow.Publisher<ByteBuffer> flow) {
            flow.subscribe(this);
        }

        private String getResult() {
            try {
                return result.get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException("Could not read flow as string", e);
            }
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(ByteBuffer item) {
            builder.append(StandardCharsets.UTF_8.decode(item));
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(builder.toString());
        }
    }
}
