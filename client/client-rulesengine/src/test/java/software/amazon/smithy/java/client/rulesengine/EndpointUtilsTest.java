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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
        int result = EndpointUtils.bytesToShort(new byte[] {0, 0, 0x12, 0x34, 0, 0}, 2);

        assertEquals(0x1234, result);
    }

    @Test
    void testBytesToShortMaxValue() {
        int result = EndpointUtils.bytesToShort(new byte[] {(byte) 0xFF, (byte) 0xFF}, 0);

        assertEquals(0xFFFF, result);
    }
}
