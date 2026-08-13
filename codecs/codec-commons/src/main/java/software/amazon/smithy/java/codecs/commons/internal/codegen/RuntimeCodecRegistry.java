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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.smithy.java.core.schema.Schema;

/**
 * Concurrent cache and publication point for generated codecs.
 *
 * @param <T> generated codec interface
 */
public final class RuntimeCodecRegistry<T> {
    private static final int DEFAULT_MAX_ENTRIES = 256;
    private static final AtomicLong CLASS_SEQUENCE = new AtomicLong();

    private final RuntimeCodecBackend<T> backend;
    private final int maxEntries;
    private final RuntimeCodegenDiagnostics diagnostics = new RuntimeCodegenDiagnostics();
    private final ConcurrentHashMap<CacheKey, CompletableFuture<Result<T>>> entries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<CacheKey> accessOrder = new ConcurrentLinkedQueue<>();

    public RuntimeCodecRegistry(RuntimeCodecBackend<T> backend) {
        this(backend, DEFAULT_MAX_ENTRIES);
    }

    public RuntimeCodecRegistry(RuntimeCodecBackend<T> backend, int maxEntries) {
        this.backend = Objects.requireNonNull(backend, "backend");
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    public T get(Schema schema, Object settingsIdentity) {
        diagnostics.request();
        Schema root = schema.isMember() ? schema.memberTarget() : schema;
        Class<?> shapeClass = root.shapeClass();
        if (shapeClass == null) {
            diagnostics.fallback();
            return null;
        }
        CacheKey key = new CacheKey(shapeClass, root.hashCode(), settingsIdentity);
        CompletableFuture<Result<T>> created = new CompletableFuture<>();
        CompletableFuture<Result<T>> future = entries.putIfAbsent(key, created);
        if (future == null) {
            future = created;
            generate(root, key, created);
            evictIfNeeded();
        } else {
            diagnostics.hit();
        }
        accessOrder.offer(key);
        Result<T> result = future.join();
        if (result.codec == null) {
            diagnostics.fallback();
        }
        return result.codec;
    }

    public RuntimeCodegenDiagnostics diagnostics() {
        return diagnostics;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
        accessOrder.clear();
    }

    private void generate(Schema schema, CacheKey key, CompletableFuture<Result<T>> future) {
        long start = System.nanoTime();
        try {
            RuntimeCodecPlan plan = RuntimeCodecPlan.analyze(schema);
            String generatedName = backend.lookupHost().getPackageName().replace('.', '/')
                    + "/Generated"
                    + sanitize(backend.id())
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
            long elapsed = System.nanoTime() - start;
            diagnostics.success(emission.bytecode().length, elapsed);
            future.complete(new Result<>(codec, null));
        } catch (Throwable error) {
            long elapsed = System.nanoTime() - start;
            Throwable failure = unwrap(error);
            diagnostics.failure(failure, elapsed);
            future.complete(new Result<>(null, failure));
        }
    }

    private void evictIfNeeded() {
        int attempts = 0;
        while (entries.size() > maxEntries && attempts++ < maxEntries * 2) {
            CacheKey candidate = accessOrder.poll();
            if (candidate == null) {
                return;
            }
            CompletableFuture<Result<T>> future = entries.get(candidate);
            if (future != null && future.isDone() && entries.remove(candidate, future)) {
                diagnostics.eviction();
            }
        }
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof InvocationTargetException ite && ite.getCause() != null) {
            return ite.getCause();
        }
        return error;
    }

    private static String sanitize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            result.append(Character.isJavaIdentifierPart(c) ? c : '_');
        }
        return result.toString();
    }

    private record CacheKey(Class<?> shapeClass, int schemaHash, Object settingsIdentity) {}

    private record Result<T>(T codec, Throwable failure) {}
}
