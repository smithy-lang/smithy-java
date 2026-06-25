/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.test.model.ListAllTypesInput;
import software.amazon.smithy.java.codegen.test.model.MapAllTypesInput;
import software.amazon.smithy.java.codegen.test.model.SparseListsInput;
import software.amazon.smithy.java.codegen.test.model.SparseMapsInput;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.json.JsonCodec;

/**
 * Tests that null values in dense collections throw and sparse collections preserve nulls.
 */
public class DenseSparseNullTest {

    private static final JsonCodec CODEC = JsonCodec.builder().build();

    @Test
    void throwsForNullInDenseList() {
        var json = """
                {"listOfString":["a",null,"b"]}
                """;
        assertThrows(SerializationException.class,
                () -> CODEC.deserializeShape(json.getBytes(StandardCharsets.UTF_8), ListAllTypesInput.builder()));
    }

    @Test
    void throwsForNullInDenseMap() {
        var json = """
                {"stringStringMap":{"a":"x","b":null}}
                """;
        assertThrows(SerializationException.class,
                () -> CODEC.deserializeShape(json.getBytes(StandardCharsets.UTF_8), MapAllTypesInput.builder()));
    }

    @Test
    void preservesNullInSparseList() {
        var json = """
                {"listOfString":["a",null,"b"]}
                """;
        var result = CODEC.deserializeShape(json.getBytes(StandardCharsets.UTF_8), SparseListsInput.builder());
        List<String> list = result.getListOfString();
        assertEquals(3, list.size());
        assertEquals("a", list.get(0));
        assertNull(list.get(1));
        assertEquals("b", list.get(2));
    }

    @Test
    void preservesNullInSparseMap() {
        var json = """
                {"stringStringMap":{"a":"x","b":null}}
                """;
        var result = CODEC.deserializeShape(json.getBytes(StandardCharsets.UTF_8), SparseMapsInput.builder());
        Map<String, String> map = result.getStringStringMap();
        assertEquals(2, map.size());
        assertEquals("x", map.get("a"));
        assertNull(map.get("b"));
    }
}
