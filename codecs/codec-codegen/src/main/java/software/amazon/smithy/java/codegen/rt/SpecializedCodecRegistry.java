/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;
import software.amazon.smithy.java.core.schema.Schema;

/**
 * Thread-safe cache and factory for runtime-generated specialized serializers/deserializers.
 *
 * <p>On first access, generation happens asynchronously in a background thread. Until the generated
 * code is ready, {@code getSerializer}/{@code getDeserializer} return null and callers should fall
 * back to the dispatch-based codec. The {@code warmup} method blocks until generation completes.
 */
public final class SpecializedCodecRegistry {

    private static final Logger LOG = Logger.getLogger(SpecializedCodecRegistry.class.getName());

    private final CodecProfile profile;
    private final BytecodeCodecProfile bytecodeProfile;
    private final ConcurrentHashMap<Class<?>, GeneratedStructSerializer> serializers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, GeneratedStructDeserializer> deserializers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, Future<?>> pending = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public SpecializedCodecRegistry(CodecProfile profile) {
        this.profile = profile;
        this.bytecodeProfile = null;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "smithy-codec-codegen");
            t.setDaemon(true);
            return t;
        });
    }

    public SpecializedCodecRegistry(BytecodeCodecProfile bytecodeProfile) {
        this.profile = null;
        this.bytecodeProfile = bytecodeProfile;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "smithy-codec-bytecode");
            t.setDaemon(true);
            return t;
        });
    }

    public GeneratedStructSerializer getSerializer(Schema schema, Class<?> shapeClass) {
        GeneratedStructSerializer ser = serializers.get(shapeClass);
        if (ser == null) {
            triggerAsync(schema, shapeClass);
        }
        return ser;
    }

    public GeneratedStructDeserializer getDeserializer(Schema schema, Class<?> shapeClass) {
        GeneratedStructDeserializer de = deserializers.get(shapeClass);
        if (de == null) {
            triggerAsync(schema, shapeClass);
        }
        return de;
    }

    public void warmup(Schema schema, Class<?> shapeClass) {
        if (serializers.containsKey(shapeClass) && deserializers.containsKey(shapeClass)) {
            return;
        }
        generate(schema, shapeClass);
    }

    public void warmupTransitive(Schema schema, Class<?> shapeClass) {
        warmup(schema, shapeClass);
        for (Schema member : schema.members()) {
            Schema target = member.memberTarget();
            if (target.type() == software.amazon.smithy.model.shapes.ShapeType.STRUCTURE
                    || target.type() == software.amazon.smithy.model.shapes.ShapeType.UNION) {
                Class<?> targetClass = target.shapeClass();
                if (targetClass != null) {
                    warmup(target, targetClass);
                }
            }
        }
    }

    private void triggerAsync(Schema schema, Class<?> shapeClass) {
        pending.computeIfAbsent(shapeClass, k -> executor.submit(() -> {
            try {
                generate(schema, shapeClass);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Async codegen failed for " + shapeClass.getName(), e);
            }
        }));
    }

    @SuppressFBWarnings(value = "AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION",
            justification = "Method is synchronized — containsKey + put sequence is safe")
    private synchronized void generate(Schema schema, Class<?> shapeClass) {
        if (serializers.containsKey(shapeClass) && deserializers.containsKey(shapeClass)) {
            return;
        }

        StructCodePlan plan = StructCodePlan.analyze(schema, shapeClass);
        String pkg = shapeClass.getPackageName();

        try {
            if (bytecodeProfile != null) {
                generateBytecode(plan, pkg, shapeClass);
            } else {
                generateJanino(plan, pkg, shapeClass);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Code generation failed for " + shapeClass.getName(), e);
            throw new RuntimeException("Code generation failed for " + shapeClass.getName(), e);
        }
    }

    private void generateJanino(StructCodePlan plan, String pkg, Class<?> shapeClass) throws Exception {
        if (!serializers.containsKey(shapeClass)) {
            String serClassName = shapeClass.getSimpleName() + "$" + profile.name() + "Ser";
            String serFqcn = pkg + "." + serClassName;
            String serSource = profile.generateSerializerSource(plan, serClassName, pkg);
            Class<?> serClass = JaninoCompiler.compile(serFqcn,
                    serSource,
                    shapeClass.getClassLoader(),
                    shapeClass);
            serializers.put(shapeClass,
                    (GeneratedStructSerializer) serClass.getDeclaredConstructor().newInstance());
        }

        if (!deserializers.containsKey(shapeClass)) {
            String deClassName = shapeClass.getSimpleName() + "$" + profile.name() + "De";
            String deFqcn = pkg + "." + deClassName;
            String deSource = profile.generateDeserializerSource(plan, deClassName, pkg);
            Class<?> deClass = JaninoCompiler.compile(deFqcn,
                    deSource,
                    shapeClass.getClassLoader(),
                    shapeClass);
            deserializers.put(shapeClass,
                    (GeneratedStructDeserializer) deClass.getDeclaredConstructor().newInstance());
        }
    }

    private void generateBytecode(StructCodePlan plan, String pkg, Class<?> shapeClass) throws Exception {
        MethodHandles.Lookup baseLookup = MethodHandles.privateLookupIn(
                shapeClass,
                MethodHandles.lookup());

        if (!serializers.containsKey(shapeClass)) {
            String serClassName = shapeClass.getSimpleName() + "$" + bytecodeProfile.name() + "Ser";
            BytecodeCodecProfile.GenerationResult serResult =
                    bytecodeProfile.generateSerializerBytecode(plan, serClassName, pkg);
            Class<?> serClass = loadHiddenClass(baseLookup, serResult);
            serializers.put(shapeClass,
                    (GeneratedStructSerializer) serClass.getDeclaredConstructor().newInstance());
        }

        if (!deserializers.containsKey(shapeClass)) {
            String deClassName = shapeClass.getSimpleName() + "$" + bytecodeProfile.name() + "De";
            BytecodeCodecProfile.GenerationResult deResult =
                    bytecodeProfile.generateDeserializerBytecode(plan, deClassName, pkg);
            Class<?> deClass = loadHiddenClass(baseLookup, deResult);
            deserializers.put(shapeClass,
                    (GeneratedStructDeserializer) deClass.getDeclaredConstructor().newInstance());
        }
    }

    private static Class<?> loadHiddenClass(
            MethodHandles.Lookup baseLookup,
            BytecodeCodecProfile.GenerationResult result
    ) throws IllegalAccessException {
        MethodHandles.Lookup hiddenLookup;
        if (result.classData() != null) {
            hiddenLookup = baseLookup.defineHiddenClassWithClassData(
                    result.bytecode(),
                    result.classData(),
                    true);
        } else {
            hiddenLookup = baseLookup.defineHiddenClass(
                    result.bytecode(),
                    true);
        }
        return hiddenLookup.lookupClass();
    }
}
