/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesEngineBuilderTest {

    private RulesEngineBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RulesEngineBuilder();
    }

    @Test
    void testLoadMinimalValidBytecode() throws IOException {
        byte[] bytecode = createMinimalBytecode();
        Bytecode loaded = builder.load(bytecode);

        assertNotNull(loaded);
        assertEquals(0, loaded.getConditionCount());
        assertEquals(0, loaded.getResultCount());
        assertEquals(0, loaded.getConstantPoolCount());
        assertEquals(0, loaded.getBddNodeCount());
    }

    @Test
    void testLoadBytecodeFromPath(@TempDir Path tempDir) throws IOException {
        Path bytecodeFile = tempDir.resolve("test.bytecode");
        byte[] bytecode = createMinimalBytecode();
        Files.write(bytecodeFile, bytecode);

        Bytecode loaded = builder.load(bytecodeFile);

        assertNotNull(loaded);
        assertEquals(0, loaded.getConditionCount());
        assertEquals(0, loaded.getResultCount());
    }

    @Test
    void testLoadBytecodeWithConditionsAndResults() throws IOException {
        byte[] bytecode = createBytecodeWithConditionsAndResults();
        Bytecode loaded = builder.load(bytecode);

        assertEquals(2, loaded.getConditionCount());
        assertEquals(1, loaded.getResultCount());
        assertEquals(1, loaded.getConstantPoolCount());
        assertEquals(1, loaded.getBddNodeCount());
    }

    @Test
    void testLoadBytecodeWithRegisters() throws IOException {
        byte[] bytecode = createBytecodeWithRegisters();
        Bytecode loaded = builder.load(bytecode);

        RegisterDefinition[] registers = loaded.getRegisterDefinitions();
        assertEquals(2, registers.length);
        assertEquals("param1", registers[0].name());
        assertTrue(registers[0].required());
        assertEquals("param2", registers[1].name());
        assertEquals(Boolean.TRUE, registers[1].defaultValue());
    }

    @Test
    void testLoadBytecodeWithFunctions() throws IOException {
        // Add a test function to the builder
        builder.addFunction(new RulesFunction() {
            @Override
            public int getArgumentCount() {
                return 1;
            }

            @Override
            public String getFunctionName() {
                return "testFunc";
            }

            @Override
            public Object apply1(Object arg) {
                return arg;
            }
        });

        byte[] bytecode = createBytecodeWithFunction("testFunc");
        Bytecode loaded = builder.load(bytecode);

        assertEquals(1, loaded.getFunctions().length);
        assertEquals("testFunc", loaded.getFunctions()[0].getFunctionName());
    }

    @Test
    void testLoadBytecodeWithConstants() throws IOException {
        byte[] bytecode = createBytecodeWithConstants();
        Bytecode loaded = builder.load(bytecode);

        assertEquals(4, loaded.getConstantPoolCount());
        assertEquals("test", loaded.getConstant(0));
        assertEquals(42, loaded.getConstant(1));
        assertEquals(true, loaded.getConstant(2));
        assertNull(loaded.getConstant(3));
    }

    @Test
    void testLoadInvalidMagicNumber() {
        byte[] bytecode = new byte[44];
        // Wrong magic number
        bytecode[0] = 0x12;
        bytecode[1] = 0x34;
        bytecode[2] = 0x56;
        bytecode[3] = 0x78;

        Exception ex = assertThrows(IllegalArgumentException.class, () -> builder.load(bytecode));
        assertTrue(ex.getMessage().contains("Invalid magic number"));
    }

    @Test
    void testLoadInvalidVersion() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(Bytecode.MAGIC);
        dos.writeShort(0x0202); // Wrong version

        byte[] bytecode = baos.toByteArray();
        byte[] fullBytecode = new byte[44];
        System.arraycopy(bytecode, 0, fullBytecode, 0, bytecode.length);

        Exception ex = assertThrows(IllegalArgumentException.class, () -> builder.load(fullBytecode));
        assertTrue(ex.getMessage().contains("Unsupported bytecode version"));
    }

    @Test
    void testLoadTooShortBytecode() {
        byte[] bytecode = new byte[43]; // One byte too short

        Exception ex = assertThrows(IllegalArgumentException.class, () -> builder.load(bytecode));
        assertTrue(ex.getMessage().contains("too short"));
    }

    @Test
    void testLoadNonExistentPath(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("does-not-exist.bytecode");

        assertThrows(UncheckedIOException.class, () -> builder.load(nonExistent));
    }

    @Test
    void testLoadMissingFunction() throws IOException {
        // Don't register the function that the bytecode expects
        byte[] bytecode = createBytecodeWithFunction("missingFunc");

        Exception ex = assertThrows(RulesEvaluationError.class, () -> builder.load(bytecode));
        assertTrue(ex.getMessage().contains("Missing bytecode functions"));
        assertTrue(ex.getMessage().contains("missingFunc"));
    }

    @Test
    void testLoadBytecodeWithBddNodes() throws IOException {
        byte[] bytecode = createBytecodeWithBddNodes();
        Bytecode loaded = builder.load(bytecode);

        assertEquals(2, loaded.getBddNodeCount());
        int[] nodes = loaded.getBddNodes();
        assertEquals(6, nodes.length); // 2 nodes * 3 ints each

        // First node: [0, 1, -1]
        assertEquals(0, nodes[0]);
        assertEquals(1, nodes[1]);
        assertEquals(-1, nodes[2]);

        // Second node: [1, 2, 3]
        assertEquals(1, nodes[3]);
        assertEquals(2, nodes[4]);
        assertEquals(3, nodes[5]);
    }

    @Test
    void testLoadInvalidOffsets() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Write header with invalid offsets
        dos.writeInt(Bytecode.MAGIC);
        dos.writeShort(Bytecode.VERSION);
        dos.writeShort(0); // conditions
        dos.writeShort(0); // results
        dos.writeShort(0); // registers
        dos.writeShort(0); // constants
        dos.writeShort(0); // functions
        dos.writeInt(0); // BDD nodes
        dos.writeInt(1); // BDD root
        dos.writeInt(1000); // Invalid condition offset
        dos.writeInt(2000); // Invalid result offset
        dos.writeInt(3000); // Invalid function offset
        dos.writeInt(4000); // Invalid constant offset
        dos.writeInt(5000); // Invalid BDD offset

        byte[] bytecode = baos.toByteArray();

        Exception ex = assertThrows(IllegalArgumentException.class, () -> builder.load(bytecode));
        assertTrue(ex.getMessage().contains("Invalid offsets"));
    }

    // Helper methods to create test bytecode

    private byte[] createMinimalBytecode() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Header
        dos.writeInt(Bytecode.MAGIC);
        dos.writeShort(Bytecode.VERSION);
        dos.writeShort(0); // conditions
        dos.writeShort(0); // results
        dos.writeShort(0); // registers
        dos.writeShort(0); // constants
        dos.writeShort(0); // functions
        dos.writeInt(0); // BDD nodes
        dos.writeInt(1); // BDD root (TRUE)

        // Offsets
        int headerSize = 44;
        dos.writeInt(headerSize); // condition table
        dos.writeInt(headerSize); // result table
        dos.writeInt(headerSize); // function table
        dos.writeInt(headerSize); // constant pool
        dos.writeInt(headerSize); // BDD table

        return baos.toByteArray();
    }

    private byte[] createBytecodeWithConditionsAndResults() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Header
        dos.writeInt(Bytecode.MAGIC);
        dos.writeShort(Bytecode.VERSION);
        dos.writeShort(2); // conditions
        dos.writeShort(1); // results
        dos.writeShort(0); // registers
        dos.writeShort(1); // constants
        dos.writeShort(0); // functions
        dos.writeInt(1); // BDD nodes
        dos.writeInt(2); // BDD root

        int headerSize = 44;
        int condTableSize = 2 * 4; // 2 conditions
        int resultTableSize = 4; // 1 result
        int bddTableSize = 12; // 1 node

        dos.writeInt(headerSize); // condition table offset
        dos.writeInt(headerSize + condTableSize); // result table offset
        dos.writeInt(headerSize + condTableSize + resultTableSize); // function table offset
        dos.writeInt(headerSize + condTableSize + resultTableSize + bddTableSize + 10); // constant pool offset
        dos.writeInt(headerSize + condTableSize + resultTableSize); // BDD table offset

        // Condition offsets
        dos.writeInt(headerSize + condTableSize + resultTableSize + bddTableSize); // condition 0
        dos.writeInt(headerSize + condTableSize + resultTableSize + bddTableSize + 3); // condition 1

        // Result offset
        dos.writeInt(headerSize + condTableSize + resultTableSize + bddTableSize + 6); // result 0

        // BDD nodes
        dos.writeInt(0); // var
        dos.writeInt(1); // high
        dos.writeInt(-1); // low

        // Bytecode section (minimal)
        dos.writeByte(Opcodes.LOAD_CONST);
        dos.writeByte(0);
        dos.writeByte(Opcodes.RETURN_VALUE);
        dos.writeByte(Opcodes.LOAD_CONST);
        dos.writeByte(0);
        dos.writeByte(Opcodes.RETURN_VALUE);
        dos.writeByte(Opcodes.LOAD_CONST);
        dos.writeByte(0);
        dos.writeByte(Opcodes.RETURN_VALUE);
        dos.writeByte(0); // padding

        // Constant pool
        dos.writeByte(Bytecode.CONST_NULL);

        return baos.toByteArray();
    }

    private byte[] createBytecodeWithRegisters() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Header
        dos.writeInt(Bytecode.MAGIC);
        dos.writeShort(Bytecode.VERSION);
        dos.writeShort(0); // conditions
        dos.writeShort(0); // results
        dos.writeShort(2); // registers
        dos.writeShort(0); // constants
        dos.writeShort(0); // functions
        dos.writeInt(0); // BDD nodes
        dos.writeInt(1); // BDD root

        int headerSize = 44;
        dos.writeInt(headerSize); // condition table
        dos.writeInt(headerSize); // result table
        dos.writeInt(headerSize); // function table

        // Calculate register section size
        ByteArrayOutputStream regBaos = new ByteArrayOutputStream();
        DataOutputStream regDos = new DataOutputStream(regBaos);

        // Register 1: required, no default
        writeUTF(regDos, "param1");
        regDos.writeByte(1); // required
        regDos.writeByte(0); // not temp
        regDos.writeByte(0); // no default
        regDos.writeByte(0); // no builtin

        // Register 2: optional with default
        writeUTF(regDos, "param2");
        regDos.writeByte(0); // not required
        regDos.writeByte(0); // not temp
        regDos.writeByte(1); // has default
        regDos.writeByte(Bytecode.CONST_BOOLEAN);
        regDos.writeByte(1); // true
        regDos.writeByte(0); // no builtin

        byte[] regBytes = regBaos.toByteArray();

        dos.writeInt(headerSize + regBytes.length); // constant pool
        dos.writeInt(headerSize); // BDD table

        // Write register definitions
        dos.write(regBytes);

        return baos.toByteArray();
    }

    private byte[] createBytecodeWithFunction(String functionName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Header
        dos.writeInt(Bytecode.MAGIC);
        dos.writeShort(Bytecode.VERSION);
        dos.writeShort(0); // conditions
        dos.writeShort(0); // results
        dos.writeShort(0); // registers
        dos.writeShort(0); // constants
        dos.writeShort(1); // functions
        dos.writeInt(0); // BDD nodes
        dos.writeInt(1); // BDD root

        int headerSize = 44;
        int funcTableSize = 2 + functionName.length();

        dos.writeInt(headerSize); // condition table
        dos.writeInt(headerSize); // result table
        dos.writeInt(headerSize); // function table
        dos.writeInt(headerSize + funcTableSize); // constant pool
        dos.writeInt(headerSize + funcTableSize); // BDD table

        // Function table
        writeUTF(dos, functionName);

        return baos.toByteArray();
    }

    private byte[] createBytecodeWithConstants() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Header
        dos.writeInt(Bytecode.MAGIC);
        dos.writeShort(Bytecode.VERSION);
        dos.writeShort(0); // conditions
        dos.writeShort(0); // results
        dos.writeShort(0); // registers
        dos.writeShort(4); // constants
        dos.writeShort(0); // functions
        dos.writeInt(0); // BDD nodes
        dos.writeInt(1); // BDD root

        int headerSize = 44;
        dos.writeInt(headerSize); // condition table
        dos.writeInt(headerSize); // result table
        dos.writeInt(headerSize); // function table
        dos.writeInt(headerSize); // constant pool
        dos.writeInt(headerSize); // BDD table

        // Constant pool
        dos.writeByte(Bytecode.CONST_STRING);
        writeUTF(dos, "test");

        dos.writeByte(Bytecode.CONST_INTEGER);
        dos.writeInt(42);

        dos.writeByte(Bytecode.CONST_BOOLEAN);
        dos.writeByte(1);

        dos.writeByte(Bytecode.CONST_NULL);

        return baos.toByteArray();
    }

    private byte[] createBytecodeWithBddNodes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Header
        dos.writeInt(Bytecode.MAGIC);
        dos.writeShort(Bytecode.VERSION);
        dos.writeShort(0); // conditions
        dos.writeShort(0); // results
        dos.writeShort(0); // registers
        dos.writeShort(0); // constants
        dos.writeShort(0); // functions
        dos.writeInt(2); // BDD nodes
        dos.writeInt(2); // BDD root

        int headerSize = 44;
        int bddTableSize = 2 * 12; // 2 nodes

        dos.writeInt(headerSize); // condition table
        dos.writeInt(headerSize); // result table
        dos.writeInt(headerSize); // function table
        dos.writeInt(headerSize + bddTableSize); // constant pool
        dos.writeInt(headerSize); // BDD table

        // BDD nodes
        dos.writeInt(0); // var
        dos.writeInt(1); // high
        dos.writeInt(-1); // low

        dos.writeInt(1); // var
        dos.writeInt(2); // high
        dos.writeInt(3); // low

        return baos.toByteArray();
    }

    private void writeUTF(DataOutputStream dos, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(bytes.length);
        dos.write(bytes);
    }
}
