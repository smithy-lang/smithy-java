/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.utils.SmithyInternalApi;

/** Concurrent cache and publication point for generated codecs. */
@SmithyInternalApi
public final class RuntimeCodecRegistry<T> {
    private static final AtomicLong CLASS_SEQUENCE = new AtomicLong();
    private static final System.Logger LOGGER = System.getLogger(RuntimeCodecRegistry.class.getName());

    private final RuntimeCodecBackend<T> backend;
    private volatile ClassValue<ConcurrentHashMap<CacheKey, CompletableFuture<Result<T>>>> entries =
            newEntryTable();

    public RuntimeCodecRegistry(RuntimeCodecBackend<T> backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public T get(Schema schema, Object settingsIdentity) {
        return get(schema, settingsIdentity, backend.memberSelector());
    }

    public T get(Schema schema, Object settingsIdentity, RuntimeCodecBackend.MemberSelector selector) {
        return get(schema, settingsIdentity, selector, RuntimeCodegenFeature.strict(backend.id()));
    }

    /**
     * Returns a generated codec, or null when lenient generation declines or fails.
     *
     * <p>Settings and selectors are identity-based cache keys. Strictness applies to the lookup,
     * not the cached generation outcome.
     */
    public T get(
            Schema schema,
            Object settingsIdentity,
            RuntimeCodecBackend.MemberSelector selector,
            boolean strict
    ) {
        Objects.requireNonNull(selector, "selector");
        Schema root = schema.isMember() ? schema.memberTarget() : schema;
        Class<?> shapeClass = root.shapeClass();
        if (shapeClass == null) {
            if (strict) {
                var unsupported = new UnsupportedSchemaException("No generated Java class for " + root.id());
                RuntimeCodegenStats.recordUnsupported(backend.id());
                throw new RuntimeCodegenException(
                        "Runtime codec generation is unsupported for "
                                + root.id()
                                + " using backend "
                                + backend.id(),
                        unsupported);
            }
            return null;
        }
        ConcurrentHashMap<CacheKey, CompletableFuture<Result<T>>> classEntries = entries.get(shapeClass);
        CacheKey key = new CacheKey(root, settingsIdentity, selector);
        CompletableFuture<Result<T>> created = new CompletableFuture<>();
        CompletableFuture<Result<T>> future = classEntries.putIfAbsent(key, created);
        if (future == null) {
            future = created;
            generate(root, selector, key, classEntries, created);
        }
        Result<T> result;
        try {
            result = future.join();
        } catch (CompletionException error) {
            rethrowFatal(error.getCause());
            throw error;
        }
        if (strict && result.unsupported() != null) {
            throw new RuntimeCodegenException(
                    "Runtime codec generation is unsupported for "
                            + root.id()
                            + " using backend "
                            + backend.id(),
                    result.unsupported());
        }
        if (strict && result.failure() != null) {
            throw new RuntimeCodegenException(
                    "Runtime codec generation failed for "
                            + root.id()
                            + " using backend "
                            + backend.id(),
                    result.failure());
        }
        return result.codec();
    }

    public void clear() {
        entries = newEntryTable();
    }

    private void generate(
            Schema schema,
            RuntimeCodecBackend.MemberSelector selector,
            CacheKey key,
            ConcurrentHashMap<CacheKey, CompletableFuture<Result<T>>> classEntries,
            CompletableFuture<Result<T>> future
    ) {
        try {
            RuntimeCodecPlan plan = RuntimeCodecPlan.analyze(
                    schema,
                    backend.budgets(),
                    backend.mode(),
                    selector);
            String generatedName = backend.lookupHost().getPackageName().replace('.', '/')
                    + "/Generated"
                    + sanitize(backend.id())
                    + sanitize(backend.variant())
                    + Long.toUnsignedString(CLASS_SEQUENCE.incrementAndGet(), 36);
            RuntimeCodecBackend.Emission emission = backend.emit(plan, generatedName);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    backend.lookupHost(),
                    MethodHandles.lookup());
            MethodHandles.Lookup hiddenLookup = lookup.defineHiddenClass(
                    emission.bytecode(),
                    true,
                    MethodHandles.Lookup.ClassOption.NESTMATE);
            Class<?> generatedClass = hiddenLookup.lookupClass();
            Object instance = hiddenLookup.findConstructor(generatedClass, MethodType.methodType(void.class))
                    .invoke();
            T codec = backend.codecType().cast(instance);
            RuntimeCodegenStats.recordGenerated(backend.id());
            future.complete(new Result<>(codec, null, null));
        } catch (Throwable error) {
            Throwable failure = unwrap(error);
            if (failure instanceof VirtualMachineError virtualMachineError) {
                classEntries.remove(key, future);
                future.completeExceptionally(virtualMachineError);
                throw virtualMachineError;
            }
            if (failure instanceof UnsupportedSchemaException) {
                RuntimeCodegenStats.recordUnsupported(backend.id());
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(
                            System.Logger.Level.DEBUG,
                            "Runtime codec generation is unsupported for "
                                    + schema.id()
                                    + " using backend "
                                    + backend.id(),
                            failure);
                }
                future.complete(new Result<>(null, (UnsupportedSchemaException) failure, null));
                return;
            }
            RuntimeCodegenStats.recordFailed(backend.id());
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Runtime codec generation failed for "
                            + schema.id()
                            + " using backend "
                            + backend.id()
                            + "; falling back to dispatch serde",
                    failure);
            // Cache the cause so strict and lenient callers can share the generation attempt.
            future.complete(new Result<>(null, null, failure));
        }
    }

    private ClassValue<ConcurrentHashMap<CacheKey, CompletableFuture<Result<T>>>> newEntryTable() {
        return new ClassValue<>() {
            @Override
            protected ConcurrentHashMap<CacheKey, CompletableFuture<Result<T>>> computeValue(Class<?> type) {
                return new ConcurrentHashMap<>();
            }
        };
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof InvocationTargetException ite && ite.getCause() != null) {
            return ite.getCause();
        }
        return error;
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
    }

    private static String sanitize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            result.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        return result.toString();
    }

    private static final class CacheKey {
        private final Schema schemaIdentity;
        private final Object settingsIdentity;
        private final Object selectorIdentity;
        private final int hashCode;

        private CacheKey(Schema schemaIdentity, Object settingsIdentity, Object selectorIdentity) {
            this.schemaIdentity = schemaIdentity;
            this.settingsIdentity = settingsIdentity;
            this.selectorIdentity = selectorIdentity;
            int result = 31 * System.identityHashCode(schemaIdentity)
                    + System.identityHashCode(settingsIdentity);
            this.hashCode = 31 * result + System.identityHashCode(selectorIdentity);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CacheKey that
                    && schemaIdentity == that.schemaIdentity
                    && settingsIdentity == that.settingsIdentity
                    && selectorIdentity == that.selectorIdentity;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private record Result<T>(T codec, UnsupportedSchemaException unsupported, Throwable failure) {}
}
