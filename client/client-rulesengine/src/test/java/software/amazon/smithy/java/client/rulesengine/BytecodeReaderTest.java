/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BytecodeReaderTest {

    @Test
    void testReadByte() {
        byte[] data = {0x42, 0x00, (byte) 0xFF};
        BytecodeReader reader = new BytecodeReader(data, 0);

        assertEquals(0x42, reader.readByte());
        assertEquals(0x00, reader.readByte());
        assertEquals((byte) 0xFF, reader.readByte());
    }

    @Test
    void testReadShort() {
        //            | 1      2 |      1             2    |
        byte[] data = {0x12, 0x34, (byte) 0xFF, (byte) 0xFF};
        BytecodeReader reader = new BytecodeReader(data, 0);

        assertEquals(0x1234, reader.readShort() & 0xFFFF);
        assertEquals(0xFFFF, reader.readShort() & 0xFFFF);
    }

    @Test
    void testReadInt() {
        //            |  1    2      3     4   |      1           2            3            4    |
        byte[] data = {0x12, 0x34, 0x56, 0x78, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        BytecodeReader reader = new BytecodeReader(data, 0);

        assertEquals(0x12345678, reader.readInt());
        assertEquals(-1, reader.readInt());
    }

    @Test
    void testReadUTF() {
        String testString = "Hello, World!";
        byte[] stringBytes = testString.getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[2 + stringBytes.length];
        data[0] = (byte) (stringBytes.length >> 8);
        data[1] = (byte) stringBytes.length;
        System.arraycopy(stringBytes, 0, data, 2, stringBytes.length);

        BytecodeReader reader = new BytecodeReader(data, 0);
        assertEquals(testString, reader.readUTF());
    }

    @Test
    void testReadConstantNull() {
        byte[] data = {Bytecode.CONST_NULL};
        BytecodeReader reader = new BytecodeReader(data, 0);

        assertNull(reader.readConstant());
    }

    @Test
    void testReadConstantString() {
        byte[] data = {
                Bytecode.CONST_STRING,
                0x00,
                0x05, // length = 5
                'H',
                'e',
                'l',
                'l',
                'o'
        };
        BytecodeReader reader = new BytecodeReader(data, 0);

        assertEquals("Hello", reader.readConstant());
    }

    @Test
    void testReadConstantInteger() {
        byte[] data = {
                Bytecode.CONST_INTEGER,
                0x00,
                0x00,
                0x00,
                0x2A // 42
        };
        BytecodeReader reader = new BytecodeReader(data, 0);

        assertEquals(42, reader.readConstant());
    }

    @Test
    void testReadConstantBoolean() {
        byte[] data = {
                Bytecode.CONST_BOOLEAN,
                0x01, // true
                Bytecode.CONST_BOOLEAN,
                0x00 // false
        };
        BytecodeReader reader = new BytecodeReader(data, 0);

        assertEquals(true, reader.readConstant());
        assertEquals(false, reader.readConstant());
    }

    @Test
    void testReadConstantList() {
        byte[] data = {
                Bytecode.CONST_LIST,
                0x00,
                0x03, // count = 3
                Bytecode.CONST_STRING,
                0x00,
                0x03,
                'o',
                'n',
                'e',
                Bytecode.CONST_INTEGER,
                0x00,
                0x00,
                0x00,
                0x02,
                Bytecode.CONST_BOOLEAN,
                0x01
        };
        BytecodeReader reader = new BytecodeReader(data, 0);

        List<?> list = (List<?>) reader.readConstant();
        assertEquals(3, list.size());
        assertEquals("one", list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(true, list.get(2));
    }

    @Test
    void testReadConstantMap() {
        byte[] data = {
                Bytecode.CONST_MAP,
                0x00,
                0x02, // count = 2
                0x00,
                0x03,
                'k',
                'e',
                'y', // "key"
                Bytecode.CONST_STRING,
                0x00,
                0x05,
                'v',
                'a',
                'l',
                'u',
                'e', // "value"
                0x00,
                0x03,
                'n',
                'u',
                'm', // "num"
                Bytecode.CONST_INTEGER,
                0x00,
                0x00,
                0x00,
                0x2A // 42
        };
        BytecodeReader reader = new BytecodeReader(data, 0);

        Map<?, ?> map = (Map<?, ?>) reader.readConstant();
        assertEquals(2, map.size());
        assertEquals("value", map.get("key"));
        assertEquals(42, map.get("num"));
    }

    @Test
    void testBoundsChecking() {
        byte[] data = {0x01, 0x02};
        BytecodeReader reader = new BytecodeReader(data, 0);

        reader.readByte();
        reader.readByte();

        // Should throw when trying to read past end
        assertThrows(IllegalArgumentException.class, reader::readByte);
    }

    @Test
    void testOffsetStartsAtGivenPosition() {
        byte[] data = {0x00, 0x01, 0x02, 0x03, 0x04};
        BytecodeReader reader = new BytecodeReader(data, 2);

        assertEquals(0x02, reader.readByte());
        assertEquals(0x03, reader.readByte());
        assertEquals(0x04, reader.readByte());
    }

    @Test
    void testNestedConstantDepthLimit() {
        // Create a deeply nested list that exceeds MAX_NESTING_DEPTH (100)
        byte[] data = new byte[102];
        for (int i = 0; i < 101; i++) {
            data[i] = Bytecode.CONST_LIST;
            // Each list has count = 1 (except we don't write the counts/values properly)
        }

        BytecodeReader reader = new BytecodeReader(data, 0);

        // This should throw due to excessive nesting
        assertThrows(IllegalArgumentException.class, reader::readConstant);
    }

    @Test
    void testUnknownConstantType() {
        byte[] data = {(byte) 99}; // Invalid constant type
        BytecodeReader reader = new BytecodeReader(data, 0);

        assertThrows(IllegalArgumentException.class, reader::readConstant);
    }

    @Test
    void testReadRegisterDefinitions() {
        // Build register definition data
        byte[] data = buildRegisterDefinitionData();
        BytecodeReader reader = new BytecodeReader(data, 0);

        RegisterDefinition[] defs = reader.readRegisterDefinitions(3);

        assertEquals(3, defs.length);

        // First register: required, no default, no builtin
        assertEquals("region", defs[0].name());
        assertTrue(defs[0].required());
        assertFalse(defs[0].temp());
        assertNull(defs[0].defaultValue());
        assertNull(defs[0].builtin());

        // Second register: optional with default
        assertEquals("useDualStack", defs[1].name());
        assertFalse(defs[1].required());
        assertFalse(defs[1].temp());
        assertEquals(false, defs[1].defaultValue());
        assertNull(defs[1].builtin());

        // Third register: optional with builtin
        assertEquals("endpoint", defs[2].name());
        assertFalse(defs[2].required());
        assertFalse(defs[2].temp());
        assertNull(defs[2].defaultValue());
        assertEquals("SDK::Endpoint", defs[2].builtin());
    }

    // Helper method to build register definition data
    private byte[] buildRegisterDefinitionData() {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);

            // Register 1: "region", required=true, temp=false, no default, no builtin
            dos.writeShort(6);
            dos.write("region".getBytes(StandardCharsets.UTF_8));
            dos.writeByte(1); // required
            dos.writeByte(0); // not temp
            dos.writeByte(0); // no default
            dos.writeByte(0); // no builtin

            // Register 2: "useDualStack", required=false, temp=false, default=false, no builtin
            dos.writeShort(12);
            dos.write("useDualStack".getBytes(StandardCharsets.UTF_8));
            dos.writeByte(0); // not required
            dos.writeByte(0); // not temp
            dos.writeByte(1); // has default
            dos.writeByte(Bytecode.CONST_BOOLEAN);
            dos.writeByte(0); // false
            dos.writeByte(0); // no builtin

            // Register 3: "endpoint", required=false, temp=false, no default, builtin="SDK::Endpoint"
            dos.writeShort(8);
            dos.write("endpoint".getBytes(StandardCharsets.UTF_8));
            dos.writeByte(0); // not required
            dos.writeByte(0); // not temp
            dos.writeByte(0); // no default
            dos.writeByte(1); // has builtin
            dos.writeShort(13);
            dos.write("SDK::Endpoint".getBytes(StandardCharsets.UTF_8));

            dos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
