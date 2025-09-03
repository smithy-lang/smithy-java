/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.rulesengine.traits.EndpointBddTrait;

/**
 * Compiles and loads a rules engine used to resolve endpoints based on Smithy's rules engine traits.
 */
public final class RulesEngineBuilder {

    static final List<RulesExtension> EXTENSIONS = new ArrayList<>();
    static {
        // Always include the standard builtins.
        EXTENSIONS.add(new StdExtension());

        for (var ext : ServiceLoader.load(RulesExtension.class)) {
            EXTENSIONS.add(ext);
        }
    }

    private final List<RulesExtension> extensions = new ArrayList<>();
    private final Map<String, RulesFunction> functions = new LinkedHashMap<>();
    private final Map<String, Function<Context, Object>> builtinProviders = new HashMap<>();

    public RulesEngineBuilder() {
        for (var ext : EXTENSIONS) {
            addExtension(ext);
        }
    }

    /**
     * Get the registered extensions.
     *
     * @return the extensions on the builder.
     */
    public List<RulesExtension> getExtensions() {
        return extensions;
    }

    /**
     * Get the collected builtin providers.
     *
     * @return builtin-providers.
     */
    public Map<String, Function<Context, Object>> getBuiltinProviders() {
        return builtinProviders;
    }

    /**
     * Register a function with the rules engine.
     *
     * @param fn Function to register.
     * @return the RulesEngine.
     */
    public RulesEngineBuilder addFunction(RulesFunction fn) {
        functions.put(fn.getFunctionName(), fn);
        return this;
    }

    /**
     * Manually add a RulesEngineExtension to the engine that injects functions and builtins.
     *
     * @param extension Extension to register.
     * @return the RulesEngine.
     */
    public RulesEngineBuilder addExtension(RulesExtension extension) {
        if (extensions.contains(extension)) {
            return this;
        }

        extensions.add(extension);
        extension.putBuiltinProviders(builtinProviders);
        for (var f : extension.getFunctions()) {
            addFunction(f);
        }
        return this;
    }

    /**
     * Compile BDD rules into a {@link Bytecode}.
     *
     * @param bdd BDD Rules to compile.
     * @return the compiled program.
     */
    public Bytecode compile(EndpointBddTrait bdd) {
        return new BytecodeCompiler(extensions, bdd, functions, builtinProviders).compile();
    }

    /**
     * Load bytecode from a file path.
     *
     * @param path Path to the bytecode file.
     * @return the loaded bytecode program.
     * @throws UncheckedIOException if there's an error reading the file.
     */
    public Bytecode load(Path path) {
        try {
            return load(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load bytecode from " + path, e);
        }
    }

    /**
     * Load bytecode from a byte array.
     *
     * @param data Data to load.
     * @return the loaded bytecode program.
     */
    public Bytecode load(byte[] data) {
        if (data.length < 44) {
            throw new IllegalArgumentException("Invalid bytecode: too short");
        }

        BytecodeReader reader = new BytecodeReader(data, 0);

        // Read and validate header
        int magic = reader.readInt();
        if (magic != Bytecode.MAGIC) {
            throw new IllegalArgumentException("Invalid magic number: 0x" + Integer.toHexString(magic) +
                    " (expected 0x" + Integer.toHexString(Bytecode.MAGIC) + ")");
        }

        short version = reader.readShort();
        if (version != Bytecode.VERSION) {
            int major = (version >> 8) & 0xFF;
            int minor = version & 0xFF;
            int expectedMajor = (Bytecode.VERSION >> 8) & 0xFF;
            int expectedMinor = Bytecode.VERSION & 0xFF;
            throw new IllegalArgumentException(String.format(
                    "Unsupported bytecode version: %d.%d (expected %d.%d)",
                    major,
                    minor,
                    expectedMajor,
                    expectedMinor));
        }

        // Read counts
        int conditionCount = reader.readShort() & 0xFFFF;
        int resultCount = reader.readShort() & 0xFFFF;
        int registerCount = reader.readShort() & 0xFFFF;
        int constantCount = reader.readShort() & 0xFFFF;
        int functionCount = reader.readShort() & 0xFFFF;
        int bddNodeCount = reader.readInt();
        int bddRootRef = reader.readInt();

        if (bddNodeCount < 0) {
            throw new IllegalArgumentException("Invalid counts in bytecode header");
        }

        // Read offset tables
        int conditionTableOffset = reader.readInt();
        int resultTableOffset = reader.readInt();
        int functionTableOffset = reader.readInt();
        int constantPoolOffset = reader.readInt();
        int bddTableOffset = reader.readInt();

        // Validate offsets are within bounds and in expected order
        if (conditionTableOffset < 44
                || conditionTableOffset > data.length
                || resultTableOffset < conditionTableOffset
                || resultTableOffset > data.length
                || functionTableOffset < resultTableOffset
                || functionTableOffset > data.length
                || bddTableOffset < functionTableOffset
                || bddTableOffset > data.length
                || constantPoolOffset < bddTableOffset
                || constantPoolOffset > data.length) {
            throw new IllegalArgumentException("Invalid offsets in bytecode header");
        }

        // Load condition offsets
        reader.offset = conditionTableOffset;
        int[] conditionOffsets = new int[conditionCount];
        for (int i = 0; i < conditionCount; i++) {
            conditionOffsets[i] = reader.readInt();
        }

        // Load result offsets
        reader.offset = resultTableOffset;
        int[] resultOffsets = new int[resultCount];
        for (int i = 0; i < resultCount; i++) {
            resultOffsets[i] = reader.readInt();
        }

        // Load function names and resolve them using this builder's functions
        reader.offset = functionTableOffset;
        RulesFunction[] resolvedFunctions = loadFunctions(reader, functionCount);

        // Load register definitions (after result table)
        reader.offset = resultTableOffset + (resultCount * 4);
        RegisterDefinition[] registers = reader.readRegisterDefinitions(registerCount);

        // Load BDD nodes as flat array
        reader.offset = bddTableOffset;
        int[] bddNodes = new int[bddNodeCount * 3];
        for (int i = 0; i < bddNodeCount; i++) {
            int baseIdx = i * 3;
            bddNodes[baseIdx] = reader.readInt(); // varIdx
            bddNodes[baseIdx + 1] = reader.readInt(); // high
            bddNodes[baseIdx + 2] = reader.readInt(); // low
        }

        // Find bytecode start and length
        int bytecodeStart = bddTableOffset + (bddNodeCount * 12);
        int bytecodeLength = constantPoolOffset - bytecodeStart;

        if (bytecodeLength < 0) {
            throw new IllegalArgumentException("Invalid bytecode section length");
        }

        // Extract bytecode section (with relative offsets)
        byte[] bytecode = new byte[bytecodeLength];
        System.arraycopy(data, bytecodeStart, bytecode, 0, bytecodeLength);

        // Adjust offsets to be relative to bytecode start
        for (int i = 0; i < conditionCount; i++) {
            conditionOffsets[i] -= bytecodeStart;
            if (conditionOffsets[i] < 0 || conditionOffsets[i] >= bytecodeLength) {
                throw new IllegalArgumentException("Invalid condition offset at index " + i);
            }
        }

        for (int i = 0; i < resultCount; i++) {
            resultOffsets[i] -= bytecodeStart;
            if (resultOffsets[i] < 0 || resultOffsets[i] >= bytecodeLength) {
                throw new IllegalArgumentException("Invalid result offset at index " + i);
            }
        }

        Object[] constantPool = loadConstantPool(data, constantPoolOffset, constantCount);
        return new Bytecode(
                bytecode,
                conditionOffsets,
                resultOffsets,
                registers,
                constantPool,
                resolvedFunctions,
                bddNodes,
                bddRootRef);
    }

    private RulesFunction[] loadFunctions(BytecodeReader reader, int count) {
        // First, read all function names
        String[] functionNames = new String[count];
        for (int i = 0; i < count; i++) {
            functionNames[i] = reader.readUTF();
        }

        // Now resolve the functions in the correct order using this builder's registered functions
        RulesFunction[] resolvedFunctions = new RulesFunction[count];
        List<String> missingFunctions = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String name = functionNames[i];
            RulesFunction fn = functions.get(name);
            if (fn == null) {
                missingFunctions.add(name);
            }
            resolvedFunctions[i] = fn;
        }

        // Report all missing functions
        if (!missingFunctions.isEmpty()) {
            throw new RulesEvaluationError("Missing bytecode functions: " + missingFunctions
                    + ". Available functions: " + functions.keySet());
        }

        return resolvedFunctions;
    }

    private static Object[] loadConstantPool(byte[] data, int offset, int count) {
        Object[] pool = new Object[count];
        BytecodeReader reader = new BytecodeReader(data, offset);
        for (int i = 0; i < count; i++) {
            pool[i] = reader.readConstant();
        }
        return pool;
    }
}
