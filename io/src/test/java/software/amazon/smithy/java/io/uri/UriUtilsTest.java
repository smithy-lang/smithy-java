/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.uri;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UriUtilsTest {

    static Stream<Arguments> pathCases() {
        return Stream.of(
                Arguments.of("/foo/bar", false, "foo/bar"),
                Arguments.of("/foo/bar?q=1", false, "foo/bar"),
                Arguments.of("///foo///bar///", false, "foo/bar"),
                Arguments.of("///foo///bar///", true, "foo///bar"),
                Arguments.of("/foo/bar/", false, "foo/bar"),
                Arguments.of("/a", false, "a"));
    }

    @ParameterizedTest
    @MethodSource("pathCases")
    void getPath(String uri, boolean allowEmpty, String expected) {
        assertThat(UriUtils.getPath(uri, allowEmpty), equalTo(expected));
    }

    static Stream<Arguments> queryCases() {
        return Stream.of(
                Arguments.of("/foo?bar=baz", "bar=baz"),
                Arguments.of("/foo?", ""),
                Arguments.of("/foo", null));
    }

    @ParameterizedTest
    @MethodSource("queryCases")
    void getQuery(String uri, String expected) {
        if (expected == null) {
            assertThat(UriUtils.getQuery(uri), nullValue());
        } else {
            assertThat(UriUtils.getQuery(uri), equalTo(expected));
        }
    }
}
