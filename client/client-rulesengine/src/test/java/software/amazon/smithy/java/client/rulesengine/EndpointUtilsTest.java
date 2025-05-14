/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.rulesengine.language.evaluation.value.EndpointValue;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.Identifier;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Template;
import software.amazon.smithy.utils.Pair;

public class EndpointUtilsTest {
    @ParameterizedTest
    @MethodSource("verifyObjectProvider")
    public void verifiesObjects(Object value, boolean isValid) {
        try {
            EndpointUtils.verifyObject(value);
            if (!isValid) {
                Assertions.fail("Expected " + value + " to fail");
            }
        } catch (UnsupportedOperationException e) {
            if (isValid) {
                throw e;
            }
        }
    }

    public static List<Arguments> verifyObjectProvider() throws Exception {
        return List.of(
                Arguments.of("hi", true),
                Arguments.of(1, true),
                Arguments.of(true, true),
                Arguments.of(false, true),
                Arguments.of(StringTemplate.from(Template.fromString("https://foo.com")), true),
                Arguments.of(new URI("/"), true),
                Arguments.of(List.of(true, 1), true),
                Arguments.of(Map.of("hi", List.of(true, List.of("a"))), true),
                // Invalid
                Arguments.of(Pair.of("a", "b"), false),
                Arguments.of(List.of(Pair.of("a", "b")), false),
                Arguments.of(Map.of(1, 1), false),
                Arguments.of(Map.of("a", Pair.of("a", "b")), false));
    }

    @ParameterizedTest
    @CsvSource({
            // Test cases for valid IP addresses
            "'http://192.168.1.1/index.html', true",
            "'https://192.168.1.1:8080/path', true",
            "'http://127.0.0.1/', true",
            "'https://255.255.255.255/', true",
            "'http://0.0.0.0/', true",
            "'https://1.2.3.4:8443/path?query=value', true",
            "'http://10.0.0.1', true",
            "'https://[2001:db8:85a3:8d3:1319:8a2e:370:7348]/', true",
            "'http://[::1]/', true",
            "'https://[fe80::1ff:fe23:4567:890a]:8443/', true",
            "'https://[2001:db8::1]', true",
            // Test cases for non-IP hostnames
            "'http://example.com/', false",
            "'https://www.google.com/search?q=test', false",
            "'http://subdomain.example.org:8080/path', false",
            "'https://localhost/test', false",
            // Test cases for invalid IP formats
            "'http://192.168.1/incomplete', false",
            "'https://256.1.1.1/invalid', false",
            "'http://1.2.3.4.5/toomanyparts', false",
            "'https://192.168.1.a/invalid', false",
            "'http://a.b.c.d/notdigits', false",
            "'https://192.168.1.256/outofrange', false",
            // Additional domain test cases
            "'https://domain-with-hyphens.com/', false",
            "'http://underscore_domain.org/', false",
            "'https://a.very.long.domain.name.example.com/path', false"
    })
    void testGetUriIsIp(String uriString, boolean expected) throws Exception {
        URI uri = new URI(uriString);
        boolean actual = (boolean) EndpointUtils.getUriProperty(uri, "isIp");

        assertThat(expected, equalTo(actual));
    }

    @ParameterizedTest
    @MethodSource("testConvertsValuesToObjectsProvider")
    void testConvertsValuesToObjects(Value value, Object object) {
        var converted = EndpointUtils.convertInputParamValue(value);

        assertThat(converted, equalTo(object));
    }

    public static List<Arguments> testConvertsValuesToObjectsProvider() {
        return List.of(
                Arguments.of(Value.emptyValue(), null),
                Arguments.of(Value.stringValue("hi"), "hi"),
                Arguments.of(Value.booleanValue(true), true),
                Arguments.of(Value.booleanValue(false), false),
                Arguments.of(Value.integerValue(1), 1),
                Arguments.of(Value.arrayValue(List.of(Value.integerValue(1))), List.of(1)),
                Arguments.of(Value.recordValue(Map.of(Identifier.of("hi"), Value.integerValue(1))),
                        Map.of("hi", 1))

        );
    }

    @Test
    public void throwsWhenValueUnsupported() {
        Assertions.assertThrows(RulesEvaluationError.class,
                () -> EndpointUtils.convertInputParamValue(EndpointValue.builder().url("https://foo").build()));
    }

    @Test
    public void getsUriParts() throws Exception {
        var uri = new URI("http://localhost/foo/bar");

        assertThat(EndpointUtils.getUriProperty(uri, "authority"), equalTo(uri.getAuthority()));
        assertThat(EndpointUtils.getUriProperty(uri, "scheme"), equalTo(uri.getScheme()));
        assertThat(EndpointUtils.getUriProperty(uri, "path"), equalTo("/foo/bar"));
        assertThat(EndpointUtils.getUriProperty(uri, "normalizedPath"), equalTo("/foo/bar/"));
    }

    @ParameterizedTest
    @MethodSource("convertsNodeInputsProvider")
    void convertsNodeInputs(Node value, Object object) {
        var converted = EndpointUtils.convertNodeInput(value);

        assertThat(converted, equalTo(object));
    }

    public static List<Arguments> convertsNodeInputsProvider() {
        return List.of(
                Arguments.of(Node.from("hi"), "hi"),
                Arguments.of(Node.from("hi"), "hi"),
                Arguments.of(Node.from(true), true),
                Arguments.of(Node.from(false), false),
                Arguments.of(Node.fromNodes(Node.from("aa")), List.of("aa")));
    }

    @Test
    public void throwsOnUnsupportNodeInput() {
        Assertions.assertThrows(RulesEvaluationError.class, () -> EndpointUtils.convertNodeInput(Node.from(1)));
    }
}
