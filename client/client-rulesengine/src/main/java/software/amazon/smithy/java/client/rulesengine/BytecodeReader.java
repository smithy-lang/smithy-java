/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BytecodeReader {
    private static final int MAX_NESTING_DEPTH = 100;

    final byte[] data;
    int offset;

    BytecodeReader(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    private void checkBounds(int bytesNeeded) {
        if (offset + bytesNeeded > data.length) {
            throw new IllegalArgumentException("Unexpected end of bytecode data at " + offset);
        }
    }

    byte readByte() {
        checkBounds(1);
        return data[offset++];
    }

    short readShort() {
        checkBounds(2);
        int value = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        offset += 2;
        return (short) value;
    }

    int readInt() {
        checkBounds(4);
        int value = (data[offset] & 0xFF) << 24;
        value |= (data[offset + 1] & 0xFF) << 16;
        value |= (data[offset + 2] & 0xFF) << 8;
        value |= data[offset + 3] & 0xFF;
        offset += 4;
        return value;
    }

    String readUTF() {
        int length = readShort() & 0xFFFF;
        checkBounds(length);
        String value = new String(data, offset, length, StandardCharsets.UTF_8);
        offset += length;
        return value;
    }

    Object readConstant() {
        return readConstant(0);
    }

    private Object readConstant(int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalArgumentException("Constant nesting depth exceeded maximum of " + MAX_NESTING_DEPTH);
        }

        byte type = readByte();
        return switch (type) {
            case Bytecode.CONST_NULL -> null;
            case Bytecode.CONST_STRING -> readUTF();
            case Bytecode.CONST_INTEGER -> readInt();
            case Bytecode.CONST_BOOLEAN -> readByte() != 0;
            case Bytecode.CONST_LIST -> {
                int size = readShort() & 0xFFFF;
                List<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(readConstant(depth + 1));
                }
                yield list;
            }
            case Bytecode.CONST_MAP -> {
                int size = readShort() & 0xFFFF;
                Map<String, Object> map = new LinkedHashMap<>(size);
                for (int i = 0; i < size; i++) {
                    String key = readUTF();
                    Object value = readConstant(depth + 1);
                    map.put(key, value);
                }
                yield map;
            }
            default -> throw new IllegalArgumentException("Unknown rules engine bytecode constant type: " + type);
        };
    }

    RegisterDefinition[] readRegisterDefinitions(int count) {
        RegisterDefinition[] registers = new RegisterDefinition[count];

        for (int i = 0; i < count; i++) {
            String name = readUTF();
            boolean required = readByte() != 0;
            boolean temp = readByte() != 0;

            // hasDefault
            Object defaultValue = null;
            if (readByte() != 0) {
                defaultValue = readConstant();
            }

            // hasBuiltin
            String builtin = null;
            if (readByte() != 0) {
                builtin = readUTF();
            }

            registers[i] = new RegisterDefinition(name, required, defaultValue, builtin, temp);
        }

        return registers;
    }
}
