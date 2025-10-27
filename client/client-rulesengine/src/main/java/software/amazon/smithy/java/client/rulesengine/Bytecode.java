/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.rulesengine.logic.bdd.Bdd;
import software.amazon.smithy.rulesengine.logic.bdd.BddNodeConsumer;

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
 * 4       2     Version (rolling version number, currently 1)
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
 * <h3>File Layout</h3>
 * <p>After the header, the file contains the following sections in order:
 *
 * <ol>
 *   <li><b>Condition Table</b> - Array of 4-byte offsets pointing to each condition's bytecode</li>
 *   <li><b>Result Table</b> - Array of 4-byte offsets pointing to each result's bytecode</li>
 *   <li><b>Register Definitions</b> - Array of parameter/register metadata (immediately after result table)</li>
 *   <li><b>Function Table</b> - Array of function names</li>
 *   <li><b>BDD Table</b> - Array of BDD nodes (3 ints per node)</li>
 *   <li><b>Instruction Section</b> - Compiled instructions for conditions and results</li>
 *   <li><b>Constant Pool</b> - All constants referenced by the bytecode</li>
 * </ol>
 *
 * <h3>Condition Table</h3>
 * <p>Array of 4-byte offsets pointing to the start of each condition's bytecode.
 * Each offset is absolute from the start of the file. When loaded, these are
 * adjusted to be relative to the bytecode section start for efficient access.
 *
 * <h3>Result Table</h3>
 * <p>Array of 4-byte offsets pointing to the start of each result's bytecode.
 * Each offset is absolute from the start of the file. When loaded, these are
 * adjusted to be relative to the bytecode section start for efficient access.
 *
 * <h3>Register Definitions</h3>
 * <p>Immediately follows the result table. Each register is encoded as:
 * <pre>
 * [nameLen:2][name:UTF-8][required:1][temp:1][hasDefault:1][default:?][hasBuiltin:1][builtin:?]
 * </pre>
 * Where:
 * <ul>
 *   <li>nameLen: 2-byte length of the name</li>
 *   <li>name: UTF-8 encoded parameter name</li>
 *   <li>required: 1 if this parameter must be provided (0 or 1)</li>
 *   <li>temp: 1 if this is a temporary register (0 or 1)</li>
 *   <li>hasDefault: 1 if a default value follows (0 or 1)</li>
 *   <li>default: constant value (only present if hasDefault=1)</li>
 *   <li>hasBuiltin: 1 if a builtin name follows (0 or 1)</li>
 *   <li>builtin: UTF-8 encoded builtin name (only present if hasBuiltin=1)</li>
 * </ul>
 *
 * <h3>Function Table</h3>
 * <p>Array of function names used in the bytecode. Each function name is encoded as:
 * <pre>
 * [length:2][UTF-8 bytes]
 * </pre>
 *
 * <h3>BDD Table</h3>
 * <p>Array of BDD nodes for efficient condition evaluation. Each node is encoded as 12 bytes:
 * <pre>
 * [varIdx:4][highRef:4][lowRef:4]
 * </pre>
 * Where:
 * <ul>
 *   <li>varIdx: Index of the condition to test (0-based)</li>
 *   <li>highRef: Reference to follow if condition is true</li>
 *   <li>lowRef: Reference to follow if condition is false</li>
 * </ul>
 *
 * <p>Reference encoding follows the BDD conventions:
 * <ul>
 *   <li>1: TRUE terminal</li>
 *   <li>-1: FALSE terminal</li>
 *   <li>2, 3, ...: Node references (node at index ref-1)</li>
 *   <li>-2, -3, ...: Complement node references (logical NOT)</li>
 *   <li>100_000_000+: Result terminals (100_000_000 + resultIndex)</li>
 * </ul>
 *
 * <h3>Instruction Section</h3>
 * <p>Contains the compiled bytecode instructions for all conditions and results.
 * The condition/result tables contain absolute offsets from the start of the file that point into this section.
 * Instructions use a stack-based virtual machine with opcodes defined in {@link Opcodes}. This section may include
 * control flow instructions like JT_OR_POP for short-circuit evaluation.
 *
 * <h3>Constant Pool</h3>
 * <p>Contains all constants referenced by the bytecode. Each constant is prefixed
 * with a type byte:
 *
 * <pre>
 * Type  Value   Format
 * ----  -----   ------
 * 0     NULL    (no data)
 * 1     STRING  [length:2][UTF-8 bytes]
 * 2     INTEGER [value:4]
 * 3     BOOLEAN [value:1]
 * 4     LIST    [count:2][element:?]...
 * 5     MAP     [count:2]([keyLen:2][key:UTF-8][value:?])...
 * </pre>
 *
 * <p>Lists and maps can contain nested values of any supported type, up to a maximum nesting depth of 100 levels.
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
    static final short VERSION = 1;
    static final byte CONST_NULL = 0;
    static final byte CONST_STRING = 1;
    static final byte CONST_INTEGER = 2;
    static final byte CONST_BOOLEAN = 3;
    static final byte CONST_LIST = 4;
    static final byte CONST_MAP = 5;

    private final short version;
    private final byte[] bytecode;
    private final int[] conditionOffsets;
    private final int[] resultOffsets;
    private final RegisterDefinition[] registerDefinitions;
    private final Object[] constantPool;
    private final RulesFunction[] functions;

    // BDD structure
    private final int[] bddNodes;
    private final int bddRootRef;

    final Object[] registerTemplate;
    private final int[] builtinIndices;
    private final int[] hardRequiredIndices;
    private final Map<String, Integer> inputRegisterMap;

    private Bdd bdd;

    Bytecode(
            byte[] bytecode,
            int[] conditionOffsets,
            int[] resultOffsets,
            RegisterDefinition[] registerDefinitions,
            Object[] constantPool,
            RulesFunction[] functions,
            int[] bddNodes,
            int bddRootRef
    ) {
        this(bytecode,
                conditionOffsets,
                resultOffsets,
                registerDefinitions,
                constantPool,
                functions,
                bddNodes,
                bddRootRef,
                VERSION);
    }

    Bytecode(
            byte[] bytecode,
            int[] conditionOffsets,
            int[] resultOffsets,
            RegisterDefinition[] registerDefinitions,
            Object[] constantPool,
            RulesFunction[] functions,
            int[] bddNodes,
            int bddRootRef,
            short version
    ) {
        if (bddNodes.length % 3 != 0) {
            throw new IllegalArgumentException("BDD nodes length must be multiple of 3, got: " + bddNodes.length);
        }

        this.bytecode = Objects.requireNonNull(bytecode);
        this.conditionOffsets = Objects.requireNonNull(conditionOffsets);
        this.resultOffsets = Objects.requireNonNull(resultOffsets);
        this.registerDefinitions = Objects.requireNonNull(registerDefinitions);
        this.constantPool = Objects.requireNonNull(constantPool);
        this.functions = functions;
        this.bddNodes = Objects.requireNonNull(bddNodes);
        this.bddRootRef = bddRootRef;

        this.registerTemplate = createRegisterTemplate(registerDefinitions);
        this.builtinIndices = findBuiltinIndices(registerDefinitions);
        this.hardRequiredIndices = findRequiredIndicesWithoutDefaultsOrBuiltins(registerDefinitions);
        this.inputRegisterMap = createInputRegisterMap(registerDefinitions);
        this.version = version;
    }

    /**
     * Get the bytecode version.
     *
     * @return bytecode version number.
     */
    public short getVersion() {
        return version;
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
     * Get the entire raw bytecode, including headers and the instructions.
     *
     * @return the bytecode.
     */
    public byte[] getBytecode() {
        return bytecode;
    }

    /**
     * Gets a read-only view of the instruction section from the bytecode.
     *
     * <p>This section contains the compiled opcodes for all conditions and results.
     *
     * @return a ByteBuffer view of the instruction bytes
     */
    public ByteBuffer getInstructions() {
        BytecodeReader reader = new BytecodeReader(bytecode, 0);
        reader.skipToBytecodeSection();
        int start = reader.offset;
        int end = reader.getBytecodeEndPosition();
        return ByteBuffer.wrap(bytecode, start, end - start).asReadOnlyBuffer();
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
     * @return the BDD nodes as a flat array where each node occupies 3 consecutive positions
     */
    public int[] getBddNodes() {
        return bddNodes;
    }

    /**
     * Gets the BDD representation of this bytecode.
     *
     * @return the BDD representation
     */
    public Bdd getBdd() {
        if (bdd == null) {
            bdd = new Bdd(bddRootRef, getConditionCount(), getResultCount(), getBddNodeCount(), this::streamNodes);
        }
        return bdd;
    }

    private void streamNodes(BddNodeConsumer consumer) {
        int nodeCount = bddNodes.length / 3;
        for (int i = 0; i < nodeCount; i++) {
            int baseIdx = i * 3;
            consumer.accept(bddNodes[baseIdx], bddNodes[baseIdx + 1], bddNodes[baseIdx + 2]);
        }
    }

    /**
     * Gets the number of BDD nodes.
     *
     * @return the node count
     */
    public int getBddNodeCount() {
        return bddNodes.length / 3;
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
            // Only pre-fill defaults if there's no builtin; if there's a builtin, it's handled as a fallback
            if (definitions[i].builtin() == null) {
                template[i] = definitions[i].defaultValue();
            }
        }
        return template;
    }

    private static int[] findBuiltinIndices(RegisterDefinition[] definitions) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < definitions.length; i++) {
            // Track all registers with builtins, regardless of defaults
            if (definitions[i].builtin() != null) {
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
