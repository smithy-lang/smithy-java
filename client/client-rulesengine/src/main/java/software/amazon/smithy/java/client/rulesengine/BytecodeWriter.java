/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds up bytecode incrementally.
 */
final class BytecodeWriter {
    private final ByteArrayOutputStream bytecodeStream = new ByteArrayOutputStream();
    private final List<Integer> conditionOffsets = new ArrayList<>();
    private final List<Integer> resultOffsets = new ArrayList<>();
    private final Map<Object, Integer> constantIndices = new HashMap<>();
    private final List<Object> constants = new ArrayList<>();
    private final List<String> functionNames = new ArrayList<>();

    void markConditionStart() {
        conditionOffsets.add(bytecodeStream.size());
    }

    void markResultStart() {
        resultOffsets.add(bytecodeStream.size());
    }

    void writeByte(int value) {
        bytecodeStream.write(value);
    }

    void writeShort(int value) {
        bytecodeStream.write((value >> 8) & 0xFF);
        bytecodeStream.write(value & 0xFF);
    }

    // Get or allocate constant index
    int getConstantIndex(Object value) {
        return constantIndices.computeIfAbsent(canonicalizeConstant(value), v -> {
            int index = constants.size();
            constants.add(v);
            return index;
        });
    }

    private Object canonicalizeConstant(Object value) {
        if (value instanceof String s) {
            return s.intern();
        } else {
            return value;
        }
    }

    // Register function usage
    void registerFunction(String functionName) {
        if (!functionNames.contains(functionName)) {
            functionNames.add(functionName);
        }
    }

    Bytecode build(
            RegisterDefinition[] registerDefinitions,
            RulesFunction[] functions,
            int[] bddNodes,
            int bddRootRef
    ) {
        ByteArrayOutputStream complete = new ByteArrayOutputStream();
        try {
            int bddNodeCount = bddNodes.length / 3;

            // Write header (44 bytes)
            writeHeader(complete, registerDefinitions.length, functions.length, bddNodeCount, bddRootRef);

            // Calculate where each section will be
            int headerSize = 44;
            int conditionTableSize = conditionOffsets.size() * 4;
            int resultTableSize = resultOffsets.size() * 4;
            int functionTableSize = calculateFunctionTableSize();
            int bddTableSize = bddNodeCount * 12; // Each node is 3 ints = 12 bytes

            // Write offset tables
            int resultTableOffset = headerSize + conditionTableSize;
            int functionTableOffset = resultTableOffset + resultTableSize;

            // Calculate where bytecode will start (after all tables and register defs)
            // We need to know register definition size first
            ByteArrayOutputStream regDefTemp = new ByteArrayOutputStream();
            writeRegisterDefinitions(regDefTemp, registerDefinitions);
            byte[] regDefBytes = regDefTemp.toByteArray();

            int bddTableOffset = functionTableOffset + functionTableSize + regDefBytes.length;
            int bytecodeOffset = bddTableOffset + bddTableSize;

            // Write condition offsets (adjusted to absolute positions)
            DataOutputStream dos = new DataOutputStream(complete);
            for (int offset : conditionOffsets) {
                dos.writeInt(bytecodeOffset + offset);
            }

            // Write result offsets (adjusted to absolute positions)
            for (int offset : resultOffsets) {
                dos.writeInt(bytecodeOffset + offset);
            }

            // Write function table
            writeFunctionTable(complete);

            // Write register definitions
            complete.write(regDefBytes);

            // Write BDD table
            writeBddTable(complete, bddNodes);

            // Write bytecode
            byte[] bytecode = bytecodeStream.toByteArray();
            complete.write(bytecode);

            // Write constant pool
            int constantPoolOffset = complete.size();
            writeConstantPool(complete);

            // Now patch the header with actual offsets
            byte[] data = complete.toByteArray();
            patchInt(data, 24, headerSize);
            patchInt(data, 28, resultTableOffset);
            patchInt(data, 32, functionTableOffset);
            patchInt(data, 36, constantPoolOffset);
            patchInt(data, 40, bddTableOffset);

            // Return the complete bytecode array wrapped in Bytecode
            return new Bytecode(
                    bytecode, // Just the bytecode portion
                    conditionOffsets.stream().mapToInt(Integer::intValue).toArray(),
                    resultOffsets.stream().mapToInt(Integer::intValue).toArray(),
                    registerDefinitions,
                    constants.toArray(),
                    functions,
                    bddNodes,
                    bddRootRef);

        } catch (IOException e) {
            throw new RuntimeException("Failed to build bytecode", e);
        }
    }

    private void writeHeader(
            ByteArrayOutputStream out,
            int registerCount,
            int functionCount,
            int bddNodeCount,
            int bddRootRef
    ) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(Bytecode.MAGIC);
        dos.writeShort(Bytecode.VERSION);
        dos.writeShort(conditionOffsets.size());
        dos.writeShort(resultOffsets.size());
        dos.writeShort(registerCount);
        dos.writeShort(constants.size());
        dos.writeShort(functionCount);
        dos.writeInt(bddNodeCount);
        dos.writeInt(bddRootRef);

        // These will be patched later
        dos.writeInt(0); // condition table offset
        dos.writeInt(0); // result table offset
        dos.writeInt(0); // function table offset
        dos.writeInt(0); // constant pool offset
        dos.writeInt(0); // BDD table offset
    }

    private void writeBddTable(ByteArrayOutputStream out, int[] nodes) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        int nodeCount = nodes.length / 3;
        for (int i = 0; i < nodeCount; i++) {
            int baseIdx = i * 3;
            dos.writeInt(nodes[baseIdx]); // variable index
            dos.writeInt(nodes[baseIdx + 1]); // high reference
            dos.writeInt(nodes[baseIdx + 2]); // low reference
        }
    }

    private int calculateFunctionTableSize() {
        int size = 0;
        for (String name : functionNames) {
            size += 2 + name.getBytes(StandardCharsets.UTF_8).length; // length prefix + UTF-8 bytes
        }
        return size;
    }

    private void writeFunctionTable(ByteArrayOutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        for (String name : functionNames) {
            writeUTF(dos, name);
        }
    }

    private void writeRegisterDefinitions(ByteArrayOutputStream out, RegisterDefinition[] registers)
            throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        for (RegisterDefinition reg : registers) {
            // Write name
            writeUTF(dos, reg.name());

            // Write required flag
            dos.writeByte(reg.required() ? 1 : 0);

            // Write temp flag
            dos.writeByte(reg.temp() ? 1 : 0);

            // Write default value
            if (reg.defaultValue() != null) {
                dos.writeByte(1); // hasDefault
                writeConstantValue(dos, reg.defaultValue());
            } else {
                dos.writeByte(0); // no default
            }

            // Write builtin
            if (reg.builtin() != null) {
                dos.writeByte(1); // hasBuiltin
                writeUTF(dos, reg.builtin());
            } else {
                dos.writeByte(0); // no builtin
            }
        }
    }

    private void writeConstantPool(ByteArrayOutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);

        // Write each constant
        for (Object constant : constants) {
            writeConstantValue(dos, constant);
        }
    }

    // Helper method to write a constant value
    private void writeConstantValue(DataOutputStream dos, Object value) throws IOException {
        if (value == null) {
            dos.writeByte(Bytecode.CONST_NULL);
        } else if (value instanceof String s) {
            dos.writeByte(Bytecode.CONST_STRING);
            writeUTF(dos, s);
        } else if (value instanceof Integer i) {
            dos.writeByte(Bytecode.CONST_INTEGER);
            dos.writeInt(i);
        } else if (value instanceof Boolean b) {
            dos.writeByte(Bytecode.CONST_BOOLEAN);
            dos.writeByte(b ? 1 : 0);
        } else if (value instanceof List<?> list) {
            dos.writeByte(Bytecode.CONST_LIST);
            dos.writeShort(list.size());
            for (Object element : list) {
                writeConstantValue(dos, element);
            }
        } else if (value instanceof Map<?, ?> map) {
            dos.writeByte(Bytecode.CONST_MAP);
            dos.writeShort(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IOException("Map keys must be strings, found: " + entry.getKey().getClass());
                }
                writeUTF(dos, (String) entry.getKey());
                writeConstantValue(dos, entry.getValue());
            }
        } else {
            throw new IOException("Unsupported constant type: " + value.getClass());
        }
    }

    // Helper to write UTF string (length-prefixed)
    private void writeUTF(DataOutputStream dos, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(bytes.length);
        dos.write(bytes);
    }

    // Helper to patch an int value in a byte array
    private void patchInt(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }
}
