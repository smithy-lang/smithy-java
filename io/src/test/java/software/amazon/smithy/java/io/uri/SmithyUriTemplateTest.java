/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link SmithyUriTemplate} to {@link SmithyUri#of(String, String, String, int, String, String)}.
 *
 * <p>The whole point of the template is to skip validation it can prove is unnecessary, so the only
 * thing that matters is that it accepts and rejects exactly what the validating factory does, and
 * produces the same URI when it accepts. Every case below assembles the string by hand, runs both
 * paths, and requires the same outcome — including the same rejection.
 */
class SmithyUriTemplateTest {

    @Test
    void matchesTheFactoryForHostValues() {
        // The region-and-suffix shape endpoint rules emit, plus values chosen to probe each rule the
        // host validator applies: delimiters that would escape the component, uppercase, dots, empties.
        List<String> values = List.of(
                "bucket",
                "my-bucket-name",
                "",
                "a",
                "amazonaws.com",
                "has.dots",
                ".leading",
                "trailing.",
                "double..dot",
                "UPPER",
                "MiXeD",
                "under_score",
                "has space",
                "has/slash",
                "has:colon",
                "has@at",
                "has?query",
                "has#frag",
                "has%41",
                "has%",
                "has%4",
                "héllo",
                "[::1]",
                "192.168.1.1");
        for (String value : values) {
            assertSame(() -> SmithyUriTemplate.builder()
                    .scheme("https")
                    .addHostLiteral("svc.")
                    .addHostPlaceholder()
                    .addHostLiteral(".amazonaws.com")
                    .addPathLiteral("/x")
                    .build()
                    .expand(value),
                    "https",
                    "svc." + value + ".amazonaws.com",
                    -1,
                    "/x",
                    null);
        }
    }

    @Test
    void matchesTheFactoryForPathValues() {
        List<String> values = List.of(
                "key",
                "",
                "a/b",
                "with%20space",
                "with%",
                "with%2",
                "with?query",
                "with#frag",
                "héllo",
                "UPPER");
        for (String value : values) {
            assertSame(() -> SmithyUriTemplate.builder()
                    .scheme("https")
                    .addHostLiteral("svc.amazonaws.com")
                    .addPathLiteral("/")
                    .addPathPlaceholder()
                    .build()
                    .expand(value),
                    "https",
                    "svc.amazonaws.com",
                    -1,
                    "/" + value,
                    null);
        }
    }

    @Test
    void matchesTheFactoryWithSeveralPlaceholders() {
        // Host placeholders come first, then path placeholders.
        assertSame(() -> SmithyUriTemplate.builder()
                .scheme("https")
                .addHostPlaceholder()
                .addHostLiteral(".s3.")
                .addHostPlaceholder()
                .addHostLiteral(".")
                .addHostPlaceholder()
                .addPathLiteral("/")
                .addPathPlaceholder()
                .build()
                .expand(new String[] {"bucket", "us-west-2", "amazonaws.com", "key"}),
                "https",
                "bucket.s3.us-west-2.amazonaws.com",
                -1,
                "/key",
                null);
    }

    @Test
    void matchesTheFactoryWhenPercentEscapesCrossPartBoundaries() {
        assertSame(() -> SmithyUriTemplate.builder()
                .scheme("https")
                .addHostLiteral("example.com")
                .addPathLiteral("/")
                .addPathPlaceholder()
                .addPathLiteral("20")
                .build()
                .expand("%"),
                "https",
                "example.com",
                -1,
                "/%20",
                null);

        assertSame(() -> SmithyUriTemplate.builder()
                .scheme("https")
                .addHostLiteral("example.com")
                .addPathLiteral("/%")
                .addPathPlaceholder()
                .build()
                .expand("20"),
                "https",
                "example.com",
                -1,
                "/%20",
                null);

        assertSame(() -> SmithyUriTemplate.builder()
                .scheme("https")
                .addHostLiteral("example.com")
                .addPathLiteral("/")
                .addPathPlaceholder()
                .addPathPlaceholder()
                .build()
                .expand("%", "20"),
                "https",
                "example.com",
                -1,
                "/%20",
                null);
    }

    @Test
    void expandsUsingCallerOwnedScratch() {
        var template = SmithyUriTemplate.builder()
                .scheme("https")
                .addHostPlaceholder()
                .addHostLiteral(".example.com")
                .addPathLiteral("/")
                .addPathPlaceholder()
                .build();
        Object[] values = {"bucket", "key"};
        char[] scratch = new char[128];
        assertEquals(
                "https://bucket.example.com/key",
                template.expandInternal(values, 0, ignored -> scratch).toString());
    }

    @Test
    void matchesTheFactoryWhenTheHostIsEntirelyFromValues() {
        // Nothing constant to prove the host non-empty, so an empty value must still be rejected the
        // same way the factory rejects it.
        for (String value : List.of("host", "", "a", "[::1]", "[not-ipv6]")) {
            assertSame(() -> SmithyUriTemplate.builder()
                    .scheme("https")
                    .addHostPlaceholder()
                    .build()
                    .expand(value),
                    "https",
                    value,
                    -1,
                    "",
                    null);
        }
    }

    @Test
    void matchesTheFactoryOnRandomizedValues() {
        char[] alphabet = {'a',
                'z',
                '0',
                '9',
                '-',
                '.',
                'A',
                '_',
                '/',
                ':',
                '@',
                '?',
                '#',
                '%',
                '4',
                ' ',
                'é'};
        var random = new Random(20261013L);
        for (int i = 0; i < 50_000; i++) {
            var chars = new char[random.nextInt(12)];
            for (int j = 0; j < chars.length; j++) {
                chars[j] = alphabet[random.nextInt(alphabet.length)];
            }
            String value = new String(chars);
            assertSame(() -> SmithyUriTemplate.builder()
                    .scheme("https")
                    .addHostLiteral("a.")
                    .addHostPlaceholder()
                    .addPathLiteral("/p")
                    .build()
                    .expand(value),
                    "https",
                    "a." + value + "/p".substring(2),
                    -1,
                    "/p",
                    null);
        }
    }

    @Test
    void rejectsAWrongNumberOfValues() {
        var template = SmithyUriTemplate.builder().scheme("https").addHostPlaceholder().build();
        assertEquals(1, template.placeholderCount());
        assertThrows(IllegalArgumentException.class, () -> template.expand(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> template.expand("a", "b"));
    }

    @Test
    void validatesConstantPartsWhenBuilt() {
        assertThrows(IllegalArgumentException.class,
                () -> SmithyUriTemplate.builder().scheme("HTTPS"));
        assertThrows(IllegalArgumentException.class,
                () -> SmithyUriTemplate.builder().port(70000));
        assertThrows(IllegalArgumentException.class,
                () -> SmithyUriTemplate.builder().addHostLiteral("has/slash"));
        assertThrows(IllegalArgumentException.class,
                () -> SmithyUriTemplate.builder().addPathLiteral("%G").addPathPlaceholder());
        assertThrows(IllegalArgumentException.class,
                () -> SmithyUriTemplate.builder().addPathLiteral("%").build());
    }

    /**
     * Runs the template and the validating factory over the same components and requires the same
     * result, treating a rejection as a result in its own right.
     */
    private static void assertSame(
            ThrowingSupplier expanded,
            String scheme,
            String host,
            int port,
            String path,
            String query
    ) {
        String fromFactory;
        try {
            fromFactory = SmithyUri.of(scheme, null, host, port, path, query).toString();
        } catch (RuntimeException e) {
            fromFactory = "rejected";
        }
        String fromTemplate;
        try {
            fromTemplate = expanded.get().toString();
        } catch (RuntimeException e) {
            fromTemplate = "rejected";
        }
        String message = "host=\"" + host + "\" path=\"" + path + "\"";
        assertEquals(fromFactory, fromTemplate, message);
    }

    private interface ThrowingSupplier {
        SmithyUri get();
    }
}
