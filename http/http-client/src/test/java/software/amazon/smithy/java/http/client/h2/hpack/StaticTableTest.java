/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2.hpack;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.http.api.HeaderNames;

class StaticTableTest {

    @Test
    void sizeIs61() {
        assertEquals(61, StaticTable.SIZE);
    }

    @ParameterizedTest(name = "index {0}: {1}={2}")
    @MethodSource("staticTableEntries")
    void getReturnsCorrectEntry(int index, String expectedName, String expectedValue) {
        HeaderField field = StaticTable.get(index);
        assertEquals(expectedName, field.name());
        assertEquals(expectedValue, field.value());
    }

    static Stream<Arguments> staticTableEntries() {
        return Stream.of(
                Arguments.of(1, ":authority", ""),
                Arguments.of(2, ":method", "GET"),
                Arguments.of(3, ":method", "POST"),
                Arguments.of(4, ":path", "/"),
                Arguments.of(5, ":path", "/index.html"),
                Arguments.of(6, ":scheme", "http"),
                Arguments.of(7, ":scheme", "https"),
                Arguments.of(8, ":status", "200"),
                Arguments.of(9, ":status", "204"),
                Arguments.of(14, ":status", "500"),
                Arguments.of(16, "accept-encoding", "gzip, deflate"),
                Arguments.of(28, "content-length", ""),
                Arguments.of(31, "content-type", ""),
                Arguments.of(38, "host", ""),
                Arguments.of(61, "www-authenticate", ""));
    }

    @ParameterizedTest(name = "findFullMatch({0}, {1}) = {2}")
    @MethodSource("fullMatchCases")
    void findFullMatch(String name, String value, int expectedIndex) {
        assertEquals(expectedIndex, StaticTable.findFullMatch(name, value));
    }

    static Stream<Arguments> fullMatchCases() {
        return Stream.of(
                Arguments.of(":method", "GET", 2),
                Arguments.of(":method", "POST", 3),
                Arguments.of(":path", "/", 4),
                Arguments.of(":path", "/index.html", 5),
                Arguments.of(":scheme", "http", 6),
                Arguments.of(":scheme", "https", 7),
                Arguments.of(":status", "200", 8),
                Arguments.of(":status", "404", 13),
                Arguments.of("accept-encoding", "gzip, deflate", 16),
                // No match cases
                Arguments.of(":method", "PUT", -1),
                Arguments.of(":status", "201", -1),
                Arguments.of("x-custom", "value", -1),
                Arguments.of("content-type", "application/json", -1));
    }

    @ParameterizedTest(name = "findNameMatch({0}) = {1}")
    @MethodSource("nameMatchCases")
    void findNameMatch(String name, int expectedIndex) {
        assertEquals(expectedIndex, StaticTable.findNameMatch(name));
    }

    static Stream<Arguments> nameMatchCases() {
        return Stream.of(
                Arguments.of(":authority", 1),
                Arguments.of(":method", 2),
                Arguments.of(":path", 4),
                Arguments.of(":scheme", 6),
                Arguments.of(":status", 8),
                Arguments.of("accept", 19),
                Arguments.of("content-length", 28),
                Arguments.of("content-type", 31),
                Arguments.of("host", 38),
                Arguments.of("www-authenticate", 61),
                // No match
                Arguments.of("x-custom", -1),
                Arguments.of("x-request-id", -1));
    }

    @Test
    void findFullMatchWithHeaderNamesConstant() {
        // Using HeaderNames constant should enable pointer comparison optimization
        assertEquals(2, StaticTable.findFullMatch(HeaderNames.PSEUDO_METHOD, "GET"));
        assertEquals(8, StaticTable.findFullMatch(HeaderNames.PSEUDO_STATUS, "200"));
    }

    @Test
    void findNameMatchWithHeaderNamesConstant() {
        assertEquals(1, StaticTable.findNameMatch(HeaderNames.PSEUDO_AUTHORITY));
        assertEquals(31, StaticTable.findNameMatch(HeaderNames.CONTENT_TYPE));
    }

    @Test
    void veryLongNameReturnsNoMatch() {
        String longName = "x".repeat(100);
        assertEquals(-1, StaticTable.findFullMatch(longName, "value"));
        assertEquals(-1, StaticTable.findNameMatch(longName));
    }
}
