/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.net.URI;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;

public class StdlibTest {
    @Test
    public void comparesStrings() {
        assertThat(Stdlib.STRING_EQUALS.apply2("a", "a"), is(true));
        assertThat(Stdlib.STRING_EQUALS.apply2("a", "b"), is(false));
        assertThat(Stdlib.STRING_EQUALS.apply2(null, "b"), is(false));

        Assertions.assertThrows(RulesEvaluationError.class, () -> Stdlib.STRING_EQUALS.apply2("a", false));
    }

    @Test
    public void comparesBooleans() {
        assertThat(Stdlib.BOOLEAN_EQUALS.apply2(true, true), is(true));
        assertThat(Stdlib.BOOLEAN_EQUALS.apply2(true, Boolean.TRUE), is(true));
        assertThat(Stdlib.BOOLEAN_EQUALS.apply2(false, Boolean.FALSE), is(true));
        assertThat(Stdlib.BOOLEAN_EQUALS.apply2(false, false), is(true));

        Assertions.assertThrows(RulesEvaluationError.class, () -> Stdlib.BOOLEAN_EQUALS.apply2("a", false));
    }

    @Test
    public void parseUrl() throws Exception {
        assertThat(Stdlib.PARSE_URL.apply1("http://foo.com"), equalTo(new URI("http://foo.com")));
        Assertions.assertThrows(RulesEvaluationError.class, () -> Stdlib.PARSE_URL.apply1(false));
        Assertions.assertThrows(RulesEvaluationError.class, () -> Stdlib.PARSE_URL.apply1("\\"));
    }

    @Test
    public void handlesSubstrings() {
        assertThat(Stdlib.SUBSTRING.apply("abc", 0, 1, false), equalTo("a"));
        assertThat(Stdlib.SUBSTRING.apply("abc", 0, 2, false), equalTo("ab"));
        assertThat(Stdlib.SUBSTRING.apply("abc", 0, 3, false), equalTo("abc"));
        assertThat(Stdlib.SUBSTRING.apply("abc", 1, 2, false), equalTo("b"));
        assertThat(Stdlib.SUBSTRING.apply("abc", 1, 3, false), equalTo("bc"));
        assertThat(Stdlib.SUBSTRING.apply("abc", 2, 3, false), equalTo("c"));

        assertThat(Stdlib.SUBSTRING.apply("abc", 2, 3, true), equalTo("a"));
        assertThat(Stdlib.SUBSTRING.apply("abc", 1, 3, true), equalTo("ab"));
    }

    @ParameterizedTest
    @CsvSource({
            // Valid simple host labels (no dots)
            "'example',false,true",
            "'a',false,true",
            "'server1',false,true",
            "'my-host',false,true",
            // Invalid simple host labels (no dots)
            "'-example',false,false",
            "'host_name',false,false",
            "'a-very-long-host-name-that-is-exactly-64-characters-in-length-1234567',false,false",
            "'',false,false",
            // Valid host labels with dots
            "'example.com',true,true",
            "'a.b.c',true,true",
            "'sub.domain.example.com',true,true",
            "'192.168.1.1',true,true",
            // Invalid host labels with dots
            "'.example.com',true,false", // Starts with dot
            "'example.com.',true,false", // Ends with dot
            "'example..com',true,false", // Double dots
            "'exam@ple.com',true,false", // Invalid character
            "'-.example.com',true,false", // Segment starts with hyphen
            "'example.c*m',true,false", // Invalid character in segment
            "'a-very-long-segment-that-is-exactly-64-characters-in-length-1234567.com',true,false" // Segment too long
    })
    public void testsForValidHostLabels(String input, boolean allowDots, boolean isValid) {
        assertThat(input, Stdlib.IS_VALID_HOST_LABEL.apply2(input, allowDots), is(isValid));
    }

    @Test
    public void resolvesSdkEndpointBuiltins() {
        var ctx = Context.create();
        var endpoint = Endpoint.builder().uri("https://foo.com").build();
        ctx.put(ClientContext.CUSTOM_ENDPOINT, endpoint);
        var result = Stdlib.standardBuiltins("SDK::Endpoint", ctx);

        assertThat(result, instanceOf(String.class));
        assertThat(result, equalTo(endpoint.uri().toString()));
    }
}
