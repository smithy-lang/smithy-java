/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private final Map<Class<?>, GeneratedStructSerializer> serializers = new ConcurrentHashMap<>();
    private final Map<Class<?>, GeneratedStructDeserializer> deserializers =
            new ConcurrentHashMap<>();
    private final Map<Class<?>, Future<?>> pending = new ConcurrentHashMap<>();
    private final Set<Class<?>> failed = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor;

    public SpecializedCodecRegistry(CodecProfile profile) {
        this.profile = profile;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "smithy-codec-bytecode");
            t.setDaemon(true);
            return t;
        });
    }

    public GeneratedStructSerializer getSerializer(Schema schema, Class<?> shapeClass) {
        GeneratedStructSerializer ser = serializers.get(shapeClass);
        if (ser == null) {
            if (failed.contains(shapeClass)) {
                return null;
            }
            triggerAsync(schema, shapeClass);
        }
        return ser;
    }

    public GeneratedStructDeserializer getDeserializer(Schema schema, Class<?> shapeClass) {
        GeneratedStructDeserializer de = deserializers.get(shapeClass);
        if (de == null) {
            if (failed.contains(shapeClass)) {
                return null;
            }
            triggerAsync(schema, shapeClass);
        }
        return de;
    }

    public void warmup(Schema schema, Class<?> shapeClass) {
        if (serializers.containsKey(shapeClass) && deserializers.containsKey(shapeClass)) {
            return;
        }
        generate(schema, shapeClass, new HashSet<>());
    }

    public void warmupTransitive(Schema schema, Class<?> shapeClass) {
        warmup(schema, shapeClass);
    }

    private void triggerAsync(Schema schema, Class<?> shapeClass) {
        if (failed.contains(shapeClass)) {
            return;
        }
        pending.computeIfAbsent(shapeClass, k -> executor.submit(() -> {
            try {
                generate(schema, shapeClass, new HashSet<>());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Async codegen failed for " + shapeClass.getName(), e);
            }
        }));
    }

    @SuppressFBWarnings(value = "AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION",
            justification = "Method is synchronized — containsKey + put sequence is safe")
    private synchronized void generate(Schema schema, Class<?> shapeClass, Set<Class<?>> inProgress) {
        if (serializers.containsKey(shapeClass) && deserializers.containsKey(shapeClass)) {
            return;
        }
        if (!inProgress.add(shapeClass)) {
            return;
        }

        StructCodePlan plan = StructCodePlan.analyze(schema, shapeClass);

        Map<String, GeneratedStructSerializer> resolvedSer = new HashMap<>();
        Map<String, GeneratedStructDeserializer> resolvedDe = new HashMap<>();
        for (Class<?> nested : plan.nestedStructClasses()) {
            generate(CodegenHelpers.schemaFor(nested), nested, inProgress);
            GeneratedStructSerializer ser = serializers.get(nested);
            if (ser != null) {
                resolvedSer.put(nested.getName(), ser);
            }
            GeneratedStructDeserializer de = deserializers.get(nested);
            if (de != null) {
                resolvedDe.put(nested.getName(), de);
            }
        }

        String pkg = shapeClass.getPackageName();

        try {
            MethodHandles.Lookup baseLookup = MethodHandles.privateLookupIn(
                    shapeClass,
                    MethodHandles.lookup());

            if (!serializers.containsKey(shapeClass)) {
                String serClassName = shapeClass.getSimpleName() + "$" + profile.name() + "Ser";
                CodecProfile.GenerationResult serResult =
                        profile.generateSerializerBytecode(plan, serClassName, pkg, resolvedSer);
                Class<?> serClass = loadHiddenClass(baseLookup, serResult);
                serializers.put(shapeClass,
                        (GeneratedStructSerializer) serClass.getDeclaredConstructor().newInstance());
            }

            if (!deserializers.containsKey(shapeClass)) {
                String deClassName = shapeClass.getSimpleName() + "$" + profile.name() + "De";
                CodecProfile.GenerationResult deResult =
                        profile.generateDeserializerBytecode(plan, deClassName, pkg, resolvedDe);
                Class<?> deClass = loadHiddenClass(baseLookup, deResult);
                deserializers.put(shapeClass,
                        (GeneratedStructDeserializer) deClass.getDeclaredConstructor().newInstance());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Code generation failed for " + shapeClass.getName(), e);
            failed.add(shapeClass);
        }
    }

    private static Class<?> loadHiddenClass(
            MethodHandles.Lookup baseLookup,
            CodecProfile.GenerationResult result
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
