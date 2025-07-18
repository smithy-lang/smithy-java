/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.java.client.rulesengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiled bytecode representation of endpoint rules.
 *
 * <p>This class represents a compiled rules program that can be efficiently evaluated
 * by a bytecode interpreter. The bytecode format is designed for fast loading and
 * minimal memory allocation during evaluation.
 *
 * <h2>Binary Format</h2>
 *
 * <p>The bytecode uses a binary format with the following structure:
 *
 * <h3>Header (44 bytes)</h3>
 * <pre>
 * Offset  Size  Description
 * ------  ----  -----------
 * 0       4     Magic number (0x52554C45 = "RULE")
 * 4       2     Version (major.minor, currently 0x0101 = 1.1)
 * 6       2     Condition count (unsigned short)
 * 8       2     Result count (unsigned short)
 * 10      2     Register count (unsigned short)
 * 12      2     Constant pool size (unsigned short)
 * 14      2     Function count (unsigned short)
 * 16      4     BDD node count
 * 20      4     BDD root reference
 * 24      4     Condition table offset
 * 28      4     Result table offset
 * 32      4     Function table offset
 * 36      4     Constant pool offset
 * 40      4     BDD table offset
 * </pre>
 *
 * <h3>Condition Table</h3>
 * <p>Array of 4-byte offsets pointing to the start of each condition's bytecode.
 * Each offset is absolute from the start of the file.
 *
 * <h3>Result Table</h3>
 * <p>Array of 4-byte offsets pointing to the start of each result's bytecode.
 * Each offset is absolute from the start of the file.
 *
 * <h3>Function Table</h3>
 * <p>Array of function names, each encoded as [length:2][UTF-8 bytes].
 *
 * <h3>Register Definitions</h3>
 * <p>Each register is encoded as:
 * <pre>
 * [nameLen:2][name:UTF-8][required:1][temp:1][hasDefault:1][default:?][hasBuiltin:1][builtin:?]
 *
 * Where:
 * - nameLen: 2-byte length of the name
 * - name: UTF-8 encoded parameter name
 * - required: 1 if this parameter must be provided (0 or 1)
 * - temp: 1 if this is a temporary register (0 or 1)
 * - hasDefault: 1 if a default value follows (0 or 1)
 * - default: constant value (only present if hasDefault=1)
 * - hasBuiltin: 1 if a builtin name follows (0 or 1)
 * - builtin: UTF-8 encoded builtin name (only present if hasBuiltin=1)
 * </pre>
 *
 * <h3>BDD Table</h3>
 * <p>Array of BDD nodes, each encoded as 12 bytes:
 * <pre>
 * [varIdx:4][highRef:4][lowRef:4]
 *
 * Where:
 * - varIdx: Variable (condition) index
 * - highRef: Reference to follow when condition is true
 * - lowRef: Reference to follow when condition is false
 * </pre>
 *
 * <h3>Bytecode Section</h3>
 * <p>Contains the compiled bytecode instructions for all conditions and results.
 * The condition/result tables point to offsets within this section.
 *
 * <h3>Constant Pool</h3>
 * <p>Contains all constants referenced by the bytecode. Each constant is prefixed
 * with a type byte:
 *
 * <pre>
 * Type  Value  Format
 * ----  -----  ------
 * 0     NULL   (no data)
 * 1     STRING [length:2][UTF-8 bytes]
 * 2     INTEGER [value:4]
 * 3     BOOLEAN [value:1]
 * 4     LIST   [count:2][element:?]...
 * 5     MAP    [count:2]([keyLen:2][key:UTF-8][value:?])...
 * </pre>
 *
 * <p>Lists and maps can contain nested values of any supported type.
 *
 * <h2>Usage</h2>
 *
 * <p>Loading from disk:
 * <pre>{@code
 * byte[] data = Files.readAllBytes(Path.of("rules.bytecode"));
 * Bytecode bytecode = engine.load(data);
 * }</pre>
 *
 * <p>Building new bytecode:
 * <pre>{@code
 * BytecodeCompiler compiler = new BytecodeCompiler(...);
 * Bytecode bytecode = compiler.compile();
 * }</pre>
 */
public final class Bytecode {

    static final int MAGIC = 0x52554C45; // "RULE"
    static final short VERSION = 0x0101; // 1.1
    static final byte CONST_NULL = 0;
    static final byte CONST_STRING = 1;
    static final byte CONST_INTEGER = 2;
    static final byte CONST_BOOLEAN = 3;
    static final byte CONST_LIST = 4;
    static final byte CONST_MAP = 5;

    private final byte[] bytecode;
    private final int[] conditionOffsets;
    private final int[] resultOffsets;
    private final RegisterDefinition[] registerDefinitions;
    private final Object[] constantPool;
    private final RulesFunction[] functions;

    // BDD structure
    private final int[][] bddNodes;
    private final int bddRootRef;

    // Register management - pre-computed for efficiency
    final Object[] registerTemplate;
    private final int[] builtinIndices;
    private final int[] hardRequiredIndices;
    private final Map<String, Integer> inputRegisterMap;

    Bytecode(byte[] bytecode, int[] conditionOffsets, int[] resultOffsets,
            RegisterDefinition[] registerDefinitions, Object[] constantPool,
            RulesFunction[] functions, int[][] bddNodes, int bddRootRef) {
        this.bytecode = Objects.requireNonNull(bytecode);
        this.conditionOffsets = Objects.requireNonNull(conditionOffsets);
        this.resultOffsets = Objects.requireNonNull(resultOffsets);
        this.registerDefinitions = Objects.requireNonNull(registerDefinitions);
        this.constantPool = Objects.requireNonNull(constantPool);
        this.functions = functions;
        this.bddNodes = Objects.requireNonNull(bddNodes);
        this.bddRootRef = bddRootRef;

        this.registerTemplate = createRegisterTemplate(registerDefinitions);
        this.builtinIndices = findBuiltinIndicesWithoutDefaults(registerDefinitions);
        this.hardRequiredIndices = findRequiredIndicesWithoutDefaultsOrBuiltins(registerDefinitions);
        this.inputRegisterMap = createInputRegisterMap(registerDefinitions);
    }

    /**
     * Get the number of conditions in the bytecode.
     *
     * @return the count of conditions.
     */
    public int getConditionCount() {
        return conditionOffsets.length;
    }

    /**
     * Gets the start offset for a condition.
     *
     * @param conditionIndex the condition index
     * @return the start offset in the bytecode
     */
    public int getConditionStartOffset(int conditionIndex) {
        return conditionOffsets[conditionIndex];
    }

    /**
     * Get the bytecode offset of a result.
     *
     * @param resultIndex Result index.
     * @return the bytecode offset of the result.
     */
    public int getResultOffset(int resultIndex) {
        return resultOffsets[resultIndex];
    }

    /**
     * Get the number of results in the bytecode.
     *
     * @return the result count.
     */
    public int getResultCount() {
        return resultOffsets.length;
    }

    /**
     * Get a specific constant from the constant pool by index.
     *
     * @param constantIndex Constant index to get.
     * @return the constant.
     */
    public Object getConstant(int constantIndex) {
        return constantPool[constantIndex];
    }

    /**
     * Get the number of constants in the pool.
     *
     * @return return the number of constants in the constant pool.
     */
    public int getConstantPoolCount() {
        return constantPool.length;
    }

    /**
     * Get the constant pool array.
     *
     * @return the constant pool.
     */
    public Object[] getConstantPool() {
        return constantPool;
    }

    /**
     * Get the functions used in this bytecode.
     *
     * @return the functions array.
     */
    public RulesFunction[] getFunctions() {
        return functions;
    }

    /**
     * Get the raw bytecode.
     *
     * @return the bytecode.
     */
    public byte[] getBytecode() {
        return bytecode;
    }

    /**
     * Get the register definitions for the bytecode (both input parameters and temp registers).
     *
     * @return the register definitions.
     */
    public RegisterDefinition[] getRegisterDefinitions() {
        return registerDefinitions;
    }

    /**
     * Get the BDD nodes array.
     *
     * @return the BDD nodes where each node is [varIdx, highRef, lowRef]
     */
    public int[][] getBddNodes() {
        return bddNodes;
    }

    /**
     * Get the BDD root reference.
     *
     * @return the root reference for BDD evaluation
     */
    public int getBddRootRef() {
        return bddRootRef;
    }

    /**
     * Get the register template array (package-private for RegisterFiller).
     *
     * @return the register template with default values
     */
    Object[] getRegisterTemplate() {
        return registerTemplate;
    }

    /**
     * Get the builtin indices array (package-private for RegisterFiller).
     *
     * @return the builtin indices array
     */
    int[] getBuiltinIndices() {
        return builtinIndices;
    }

    /**
     * Get the hard required indices array (package-private for RegisterFiller).
     *
     * @return the hard required indices array
     */
    int[] getHardRequiredIndices() {
        return hardRequiredIndices;
    }

    /**
     * Get the input register map (package-private for RegisterFiller).
     *
     * @return the input register map
     */
    Map<String, Integer> getInputRegisterMap() {
        return inputRegisterMap;
    }

    private static Map<String, Integer> createInputRegisterMap(RegisterDefinition[] definitions) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < definitions.length; i++) {
            if (!definitions[i].temp()) {
                map.put(definitions[i].name(), i);
            }
        }
        return map;
    }

    private static Object[] createRegisterTemplate(RegisterDefinition[] definitions) {
        Object[] template = new Object[definitions.length];
        for (int i = 0; i < definitions.length; i++) {
            template[i] = definitions[i].defaultValue();
        }
        return template;
    }

    private static int[] findBuiltinIndicesWithoutDefaults(RegisterDefinition[] definitions) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < definitions.length; i++) {
            RegisterDefinition def = definitions[i];
            // Only track builtins that don't already have defaults
            if (def.builtin() != null && def.defaultValue() == null) {
                indices.add(i);
            }
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] findRequiredIndicesWithoutDefaultsOrBuiltins(RegisterDefinition[] definitions) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < definitions.length; i++) {
            RegisterDefinition def = definitions[i];
            // Only track if truly required (no default, no builtin, not temp)
            if (def.required() && def.defaultValue() == null && def.builtin() == null && !def.temp()) {
                indices.add(i);
            }
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public String toString() {
        return new BytecodeDisassembler(this).disassemble();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Bytecode other)) {
            return false;
        }
        return Arrays.equals(bytecode, other.bytecode);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytecode);
    }
}
