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
import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Thread-safe cache and factory for runtime-generated specialized serializers/deserializers.
 *
 * <p>On first access, generation happens asynchronously in a background thread. Until the generated
 * code is ready, {@code getSerializer}/{@code getDeserializer} return null and callers should fall
 * back to the dispatch-based codec. The {@code warmup} method blocks until generation completes.
 */
public final class SpecializedCodecRegistry {

    private static final InternalLogger LOG = InternalLogger.getLogger(SpecializedCodecRegistry.class);

    private static final class Entry {
        final GeneratedStructSerializer[] serHolder = new GeneratedStructSerializer[1];
        final GeneratedStructDeserializer[] deHolder = new GeneratedStructDeserializer[1];
        volatile boolean failed;
        boolean triggered; // guarded by SpecializedCodecRegistry.this monitor
    }

    private final CodecProfile profile;
    private final ConcurrentHashMap<Class<?>, Entry> entries = new ConcurrentHashMap<>();
    private final ClassValue<Entry> entryCache = new ClassValue<>() {
        @Override
        protected Entry computeValue(Class<?> type) {
            return entries.computeIfAbsent(type, k -> new Entry());
        }
    };
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
        Entry entry = entryCache.get(shapeClass);
        if (entry.failed) {
            return null;
        }
        GeneratedStructSerializer ser = entry.serHolder[0];
        if (ser != null) {
            return ser;
        }
        triggerAsync(schema, shapeClass);
        return null;
    }

    public GeneratedStructDeserializer getDeserializer(Schema schema, Class<?> shapeClass) {
        Entry entry = entryCache.get(shapeClass);
        if (entry.failed) {
            return null;
        }
        GeneratedStructDeserializer de = entry.deHolder[0];
        if (de != null) {
            return de;
        }
        triggerAsync(schema, shapeClass);
        return null;
    }

    public void warmup(Schema schema, Class<?> shapeClass) {
        Entry entry = entryCache.get(shapeClass);
        if (entry.serHolder[0] != null && entry.deHolder[0] != null) {
            return;
        }
        generate(schema, shapeClass, new HashSet<>());
    }

    private void triggerAsync(Schema schema, Class<?> shapeClass) {
        Entry entry = getOrCreateEntry(shapeClass);
        if (entry.failed) {
            return;
        }
        synchronized (this) {
            if (entry.triggered) {
                return;
            }
            entry.triggered = true;
        }
        executor.submit(() -> {
            try {
                generate(schema, shapeClass, new HashSet<>());
            } catch (Exception e) {
                LOG.warn("Async codegen failed for " + shapeClass.getName(), e);
            }
        });
    }

    @SuppressFBWarnings(value = "AT_OPERATION_SEQUENCE_ON_CONCURRENT_ABSTRACTION",
            justification = "Method is synchronized, containsKey + put sequence is safe")
    private synchronized void generate(Schema schema, Class<?> shapeClass, Set<Class<?>> inProgress) {
        Entry entry = getOrCreateEntry(shapeClass);
        if (entry.serHolder[0] != null && entry.deHolder[0] != null) {
            return;
        }
        if (!inProgress.add(shapeClass)) {
            return;
        }

        StructCodePlan plan = StructCodePlan.analyze(schema, shapeClass);

        Map<String, GeneratedStructSerializer[]> nestedSerHolders = new HashMap<>();
        Map<String, GeneratedStructDeserializer[]> nestedDeHolders = new HashMap<>();
        for (Class<?> nested : plan.nestedStructClasses()) {
            Entry nestedEntry = getOrCreateEntry(nested);
            generate(CodegenHelpers.schemaFor(nested), nested, inProgress);
            String nestedName = nested.getName();
            nestedSerHolders.put(nestedName, nestedEntry.serHolder);
            nestedDeHolders.put(nestedName, nestedEntry.deHolder);
        }

        String pkg = shapeClass.getPackageName();

        try {
            MethodHandles.Lookup baseLookup = MethodHandles.privateLookupIn(
                    shapeClass,
                    MethodHandles.lookup());

            if (entry.serHolder[0] == null) {
                String serClassName = shapeClass.getSimpleName() + "$" + profile.name() + "Ser";
                CodecProfile.GenerationResult serResult =
                        profile.generateSerializerBytecode(plan, serClassName, pkg, nestedSerHolders);
                Class<?> serClass = loadHiddenClass(baseLookup, serResult);
                entry.serHolder[0] =
                        (GeneratedStructSerializer) serClass.getDeclaredConstructor().newInstance();
            }

            if (entry.deHolder[0] == null) {
                String deClassName = shapeClass.getSimpleName() + "$" + profile.name() + "De";
                CodecProfile.GenerationResult deResult =
                        profile.generateDeserializerBytecode(plan, deClassName, pkg, nestedDeHolders);
                Class<?> deClass = loadHiddenClass(baseLookup, deResult);
                entry.deHolder[0] =
                        (GeneratedStructDeserializer) deClass.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            LOG.warn("Code generation failed for " + shapeClass.getName(), e);
            entry.failed = true;
        }
    }

    private Entry getOrCreateEntry(Class<?> shapeClass) {
        return entryCache.get(shapeClass);
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
