/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.rulesengine.language.evaluation.value.Value;
import software.amazon.smithy.rulesengine.language.syntax.Identifier;

class EndpointUtilsTest {

    @Test
    void testConvertStringNode() {
        assertEquals("test-value", EndpointUtils.convertNode(StringNode.from("test-value")));
    }

    @Test
    void testConvertBooleanNode() {
        assertEquals(true, EndpointUtils.convertNode(BooleanNode.from(true)));
        assertEquals(false, EndpointUtils.convertNode(BooleanNode.from(false)));
    }

    @Test
    void testConvertArrayNode() {
        Object result = EndpointUtils.convertNode(ArrayNode.fromStrings("item1", "item2", "item3"));

        assertInstanceOf(List.class, result);
        List<?> list = (List<?>) result;
        assertEquals(3, list.size());
        assertEquals("item1", list.get(0));
        assertEquals("item2", list.get(1));
        assertEquals("item3", list.get(2));
    }

    @Test
    void testConvertNestedArrayNode() {
        var arr = ArrayNode.fromNodes(StringNode.from("first"), ArrayNode.fromStrings("nested1", "nested2"));
        var result = EndpointUtils.convertNode(arr);

        assertInstanceOf(List.class, result);
        List<?> list = (List<?>) result;
        assertEquals(2, list.size());
        assertEquals("first", list.get(0));

        assertInstanceOf(List.class, list.get(1));
        List<?> nestedList = (List<?>) list.get(1);
        assertEquals(2, nestedList.size());
        assertEquals("nested1", nestedList.get(0));
        assertEquals("nested2", nestedList.get(1));
    }

    @Test
    void testConvertNumberNodeWithAllowAll() {
        assertEquals(42, EndpointUtils.convertNode(NumberNode.from(42), true));
    }

    @Test
    void testConvertObjectNodeWithAllowAll() {
        ObjectNode node = ObjectNode.builder()
                .withMember("key1", StringNode.from("value1"))
                .withMember("key2", NumberNode.from(123))
                .build();
        Object result = EndpointUtils.convertNode(node, true);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("value1", map.get("key1"));
        assertEquals(123, map.get("key2"));
    }

    @Test
    void testConvertNullNodeWithAllowAll() {
        assertNull(EndpointUtils.convertNode(NullNode.nullNode(), true));
    }

    @Test
    void testConvertUnsupportedNodeThrows() {
        NumberNode node = NumberNode.from(42);

        assertThrows(RulesEvaluationError.class, () -> EndpointUtils.convertNode(node, false));
    }

    @Test
    void testConvertToValueString() {
        assertEquals("test", EndpointUtils.convertToValue("test").expectStringValue().getValue());
    }

    @Test
    void testConvertToValueNumber() {
        assertEquals(42, EndpointUtils.convertToValue(42).expectIntegerValue().getValue());
    }

    @Test
    void testConvertToValueBoolean() {
        assertTrue(EndpointUtils.convertToValue(true).expectBooleanValue().getValue());
        assertFalse(EndpointUtils.convertToValue(false).expectBooleanValue().getValue());
    }

    @Test
    void testConvertToValueNull() {
        assertTrue(EndpointUtils.convertToValue(null).isEmpty());
    }

    @Test
    void testConvertToValueList() {
        List<Object> list = List.of("item1", 42, true);
        Value value = EndpointUtils.convertToValue(list);
        List<Value> arrayValues = value.expectArrayValue().getValues();

        assertEquals(3, arrayValues.size());
        assertEquals("item1", arrayValues.get(0).expectStringValue().getValue());
        assertEquals(42, arrayValues.get(1).expectIntegerValue().getValue());
        assertTrue(arrayValues.get(2).expectBooleanValue().getValue());
    }

    @Test
    void testConvertToValueMap() {
        Map<String, Object> map = Map.of("key1", "value1", "key2", 123);
        Value value = EndpointUtils.convertToValue(map);
        Map<Identifier, Value> recordMap = value.expectRecordValue().getValue();

        assertEquals("value1", recordMap.get(Identifier.of("key1")).expectStringValue().getValue());
        assertEquals(123, recordMap.get(Identifier.of("key2")).expectIntegerValue().getValue());
    }

    @Test
    void testBytesToShort() {
        assertEquals(0x1234, EndpointUtils.bytesToShort(new byte[] {0, 0, 0x12, 0x34, 0, 0}, 2));
        assertEquals(0xFFFF, EndpointUtils.bytesToShort(new byte[] {(byte) 0xFF, (byte) 0xFF}, 0));
    }

    static Stream<Arguments> getPropertyCases() {
        URI uri = URI.create("https://example.com/foo/bar");
        Map<String, Object> map = Map.of("key1", "value1", "key2", 42);
        return Stream.of(
                Arguments.of(map, "key1", "value1"),
                Arguments.of(map, "key2", 42),
                Arguments.of(map, "missing", null),
                Arguments.of(uri, "scheme", "https"),
                Arguments.of(uri, "authority", "example.com"),
                Arguments.of(uri, "path", "/foo/bar"),
                Arguments.of(uri, "normalizedPath", "/foo/bar/"),
                Arguments.of(uri, "isIp", false),
                Arguments.of(uri, "unknown", null),
                Arguments.of(null, "key", null),
                Arguments.of("not a map", "key", null));
    }

    @ParameterizedTest
    @MethodSource("getPropertyCases")
    void testGetProperty(Object target, String property, Object expected) {
        assertEquals(expected, EndpointUtils.getProperty(target, property));
    }

    @Test
    void testGetPropertyIsIpTrue() {
        URI uri = URI.create("https://192.168.1.1/path");
        assertEquals(true, EndpointUtils.getProperty(uri, "isIp"));
    }

    static Stream<Arguments> getIndexCases() {
        List<String> list = List.of("a", "b", "c");
        return Stream.of(
                Arguments.of(list, 0, "a"),
                Arguments.of(list, 1, "b"),
                Arguments.of(list, 2, "c"),
                Arguments.of(list, 3, null),
                Arguments.of(list, -1, null),
                Arguments.of(List.of(), 0, null),
                Arguments.of(null, 0, null),
                Arguments.of("not a list", 0, null));
    }

    @ParameterizedTest
    @MethodSource("getIndexCases")
    void testGetIndex(Object target, int index, Object expected) {
        assertEquals(expected, EndpointUtils.getIndex(target, index));
    }

    static Stream<Arguments> getNegativeIndexCases() {
        List<String> list = List.of("a", "b", "c");
        return Stream.of(
                Arguments.of(list, 1, "c"),
                Arguments.of(list, 2, "b"),
                Arguments.of(list, 3, "a"),
                Arguments.of(list, 4, null),
                Arguments.of(List.of(), 1, null),
                Arguments.of(null, 1, null),
                Arguments.of("not a list", 1, null));
    }

    @ParameterizedTest
    @MethodSource("getNegativeIndexCases")
    void testGetNegativeIndex(Object target, int negIndex, Object expected) {
        assertEquals(expected, EndpointUtils.getNegativeIndex(target, negIndex));
    }

    static Stream<Arguments> substringEqualsCases() {
        return Stream.of(
                Arguments.of("hello world", 0, 5, false, "hello", true),
                Arguments.of("hello world", 6, 11, false, "world", true),
                Arguments.of("hello world", 0, 5, false, "world", false),
                Arguments.of("hello world", 0, 5, true, "world", true),
                Arguments.of("hello world", 6, 11, true, "hello", true),
                Arguments.of(null, 0, 5, false, "hello", false),
                Arguments.of("hello", 0, 5, false, null, false),
                Arguments.of("hi", 0, 10, false, "hello", false),
                Arguments.of("hello", 0, 10, true, "hello", false),
                Arguments.of("hello", 3, 3, false, "", false));
    }

    @ParameterizedTest
    @MethodSource("substringEqualsCases")
    void testSubstringEquals(String value, int start, int end, boolean reverse, String expected, boolean result) {
        assertEquals(result, EndpointUtils.substringEquals(value, start, end, reverse, expected));
    }

    static Stream<Arguments> splitGetCases() {
        return Stream.of(
                Arguments.of("a.b.c", ".", 0, "a"),
                Arguments.of("a.b.c", ".", 1, "b"),
                Arguments.of("a.b.c", ".", 2, "c"),
                Arguments.of("a.b.c", ".", -1, "c"),
                Arguments.of("a.b.c", ".", -2, "b"),
                Arguments.of("a.b.c", ".", -3, "a"),
                Arguments.of("a.b.c", ".", 5, null),
                Arguments.of("a.b.c", ".", -5, null),
                Arguments.of(null, ".", 0, null),
                Arguments.of("a.b.c", null, 0, null),
                Arguments.of("a.b.c", "", 0, null),
                Arguments.of("a::b::c", "::", 1, "b"),
                Arguments.of("abc", ".", 0, "abc"),
                Arguments.of("abc", ".", 1, null));
    }

    @ParameterizedTest
    @MethodSource("splitGetCases")
    void testSplitGet(String value, String delimiter, int index, Object expected) {
        assertEquals(expected, EndpointUtils.splitGet(value, delimiter, index));
    }
}
