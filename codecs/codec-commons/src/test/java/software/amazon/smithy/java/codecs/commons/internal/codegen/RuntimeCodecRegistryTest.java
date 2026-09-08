/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassWriter;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.MethodVisitor;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Type;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public final class RuntimeCodecRegistryTest {
    private static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("example#TestShape"))
            .shapeClass(TestShape.class)
            .builderSupplier(Builder::new)
            .putMember("value", PreludeSchemas.STRING)
            .build();

    @Test
    void plansDirectAccessAndDefinesHiddenClass() {
        var backend = new TestBackend(false);
        var registry = new RuntimeCodecRegistry<>(backend);

        TestCodec codec = registry.get(SCHEMA, "settings");

        assertNotNull(codec);
        assertEquals("generated", codec.value());
        assertTrue(codec.acceptsBuilder(new Builder()));
        assertFalse(codec.acceptsBuilder(new OtherBuilder()));
        assertEquals(1, backend.emissions.get());
    }

    @Test
    void resolvesSetterByMemberTypeWhenBuilderMethodIsOverloaded() {
        RuntimeCodecPlan plan = RuntimeCodecPlan.analyze(SCHEMA);

        assertSame(
                String.class,
                plan.rootStructure().members().getFirst().setter().getParameterTypes()[0]);
    }

    @Test
    void deduplicatesConcurrentGenerationAndPublication() throws Exception {
        var backend = new TestBackend(false);
        var registry = new RuntimeCodecRegistry<>(backend);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<TestCodec>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return registry.get(SCHEMA, "settings");
                }));
            }
            start.countDown();
            TestCodec first = futures.getFirst().get();
            for (var future : futures) {
                assertSame(first, future.get());
            }
        }

        assertEquals(1, backend.emissions.get());
    }

    @Test
    void cachesGenerationFailure() {
        var backend = new TestBackend(new UnsupportedSchemaException("expected"));
        var registry = new RuntimeCodecRegistry<>(backend);

        assertNull(registry.get(SCHEMA, "settings"));
        assertNull(registry.get(SCHEMA, "settings"));

        assertEquals(1, backend.emissions.get());
    }

    @Test
    void cachesUnexpectedGenerationFailuresAsFallbacks() {
        var backend = new TestBackend(new IllegalStateException("generation bug"));
        var registry = new RuntimeCodecRegistry<>(backend);

        assertNull(registry.get(SCHEMA, "settings"));
        assertNull(registry.get(SCHEMA, "settings"));

        assertEquals(1, backend.emissions.get());
    }

    @Test
    void retainsMoreThanPreviousDefaultLimit() {
        var backend = new TestBackend(false);
        var registry = new RuntimeCodecRegistry<>(backend);
        List<Object> settings = new ArrayList<>();

        for (int i = 0; i < 300; i++) {
            Object identity = new Object();
            settings.add(identity);
            assertNotNull(registry.get(SCHEMA, identity));
        }

        assertEquals(300, backend.emissions.get());
        assertNotNull(registry.get(SCHEMA, settings.getFirst()));
        assertEquals(300, backend.emissions.get());
    }

    @Test
    void settingsCacheKeyUsesIdentity() {
        var backend = new TestBackend(false);
        var registry = new RuntimeCodecRegistry<>(backend);

        assertNotNull(registry.get(SCHEMA, new String("settings")));
        assertNotNull(registry.get(SCHEMA, new String("settings")));

        assertEquals(2, backend.emissions.get());
    }

    @Test
    void clearReplacesClassScopedEntries() {
        var backend = new TestBackend(false);
        var registry = new RuntimeCodecRegistry<>(backend);

        assertNotNull(registry.get(SCHEMA, "settings"));
        registry.clear();
        assertNotNull(registry.get(SCHEMA, "settings"));

        assertEquals(2, backend.emissions.get());
    }

    @Test
    void fatalVmErrorsAreNotConvertedToFallbacks() {
        var failure = new OutOfMemoryError("expected");
        var backend = new TestBackend(failure);
        var registry = new RuntimeCodecRegistry<>(backend);

        assertSame(failure, assertThrows(OutOfMemoryError.class, () -> registry.get(SCHEMA, "settings")));
        assertSame(failure, assertThrows(OutOfMemoryError.class, () -> registry.get(SCHEMA, "settings")));
        assertEquals(2, backend.emissions.get());
    }

    @Test
    void countsGenerationOutcomesPerBackend() {
        RuntimeCodegenStats.reset();

        assertNotNull(new RuntimeCodecRegistry<>(new TestBackend((Throwable) null, "stats")).get(SCHEMA, "settings"));
        assertNull(new RuntimeCodecRegistry<>(new TestBackend(new UnsupportedSchemaException("nope"), "stats"))
                .get(SCHEMA, "settings"));
        assertNull(new RuntimeCodecRegistry<>(new TestBackend(new IllegalStateException("bug"), "stats"))
                .get(SCHEMA, "settings"));

        var snapshot = RuntimeCodegenStats.snapshot("stats");
        assertEquals(1, snapshot.generated());
        assertEquals(1, snapshot.unsupported());
        assertEquals(1, snapshot.failed());
        assertEquals(3, snapshot.attempts());
    }

    @Test
    void countersStayZeroForAnUnknownBackend() {
        RuntimeCodegenStats.reset();

        assertEquals(0, RuntimeCodegenStats.snapshot("never-used").attempts());
    }

    @Test
    void strictModeRethrowsGenerationBugsRatherThanFallingBack() {
        var bug = new IllegalStateException("generation bug");
        var backend = new TestBackend(bug, "strict-bug");
        var registry = new RuntimeCodecRegistry<>(backend);
        System.setProperty("smithy-java.runtime-codegen.strict-bug", "strict");
        try {
            var thrown = assertThrows(RuntimeCodegenException.class, () -> registry.get(SCHEMA, "settings"));
            assertSame(bug, thrown.getCause());

            var again = assertThrows(RuntimeCodegenException.class, () -> registry.get(SCHEMA, "settings"));
            assertSame(bug, again.getCause());
            assertEquals(1, backend.emissions.get());
        } finally {
            System.clearProperty("smithy-java.runtime-codegen.strict-bug");
        }
    }

    @Test
    void strictModeRejectsUnsupportedSchemas() {
        var unsupported = new UnsupportedSchemaException("unsupported by design");
        var backend = new TestBackend(unsupported, "strict-unsupported");
        var registry = new RuntimeCodecRegistry<>(backend);
        System.setProperty("smithy-java.runtime-codegen", "strict");
        try {
            var thrown = assertThrows(RuntimeCodegenException.class, () -> registry.get(SCHEMA, "settings"));
            assertSame(unsupported, thrown.getCause());

            var again = assertThrows(RuntimeCodegenException.class, () -> registry.get(SCHEMA, "settings"));
            assertSame(unsupported, again.getCause());
            assertEquals(1, backend.emissions.get());
        } finally {
            System.clearProperty("smithy-java.runtime-codegen");
        }
    }

    @Test
    void strictnessBelongsToTheCallNotTheCacheEntry() {
        var bug = new IllegalStateException("generation bug");
        var backend = new TestBackend(bug, "per-call-strict");
        var registry = new RuntimeCodecRegistry<>(backend);
        var all = RuntimeCodecBackend.MemberSelector.all();

        assertNull(registry.get(SCHEMA, "settings", all, false));
        var thrown = assertThrows(
                RuntimeCodegenException.class,
                () -> registry.get(SCHEMA, "settings", all, true));
        assertSame(bug, thrown.getCause());
        assertNull(registry.get(SCHEMA, "settings", all, false));
        assertEquals(1, backend.emissions.get());
    }

    @Test
    void strictCallsRejectUnsupportedSchemas() {
        var unsupported = new UnsupportedSchemaException("unsupported by design");
        var backend = new TestBackend(unsupported, "per-call-strict2");
        var registry = new RuntimeCodecRegistry<>(backend);

        var thrown = assertThrows(
                RuntimeCodegenException.class,
                () -> registry.get(SCHEMA, "settings", RuntimeCodecBackend.MemberSelector.all(), true));
        assertSame(unsupported, thrown.getCause());
        assertNull(registry.get(SCHEMA, "settings", RuntimeCodecBackend.MemberSelector.all(), false));
        assertEquals(1, backend.emissions.get());
    }

    @Test
    void strictCallsRejectSchemasWithoutGeneratedClasses() {
        var schema = Schema.structureBuilder(ShapeId.from("example#DynamicShape"))
                .putMember("value", PreludeSchemas.STRING)
                .build();
        var registry = new RuntimeCodecRegistry<>(new TestBackend((Throwable) null, "dynamic"));

        assertNull(registry.get(schema, "settings", RuntimeCodecBackend.MemberSelector.all(), false));
        var thrown = assertThrows(
                RuntimeCodegenException.class,
                () -> registry.get(schema, "settings", RuntimeCodecBackend.MemberSelector.all(), true));
        assertTrue(thrown.getCause() instanceof UnsupportedSchemaException);
    }

    @Test
    void selectorIsASeparateCacheAxis() {
        var backend = new TestBackend(false);
        var registry = new RuntimeCodecRegistry<>(backend);
        RuntimeCodecBackend.MemberSelector narrowed = (root, member) -> true;

        assertNotNull(registry.get(SCHEMA, "settings"));
        assertNotNull(registry.get(SCHEMA, "settings", narrowed));
        assertEquals(2, backend.emissions.get());

        assertNotNull(registry.get(SCHEMA, "settings"));
        assertNotNull(registry.get(SCHEMA, "settings", narrowed));
        assertEquals(2, backend.emissions.get());
    }

    @Test
    void defaultSelectorIsStableAcrossCallsSoTheCacheStillHits() {
        var backend = new TestBackend(false);
        var registry = new RuntimeCodecRegistry<>(backend);

        assertSame(RuntimeCodecBackend.MemberSelector.all(), RuntimeCodecBackend.MemberSelector.all());
        assertNotNull(registry.get(SCHEMA, "settings"));
        assertNotNull(registry.get(SCHEMA, "settings"));
        assertNotNull(registry.get(SCHEMA, "settings", RuntimeCodecBackend.MemberSelector.all()));

        assertEquals(1, backend.emissions.get());
    }

    private interface TestCodec {
        String value();

        boolean acceptsBuilder(ShapeBuilder<?> builder);
    }

    private static final class TestBackend implements RuntimeCodecBackend<TestCodec> {
        private final AtomicInteger emissions = new AtomicInteger();
        private final Throwable failure;
        private final String id;

        TestBackend(boolean fail) {
            this(fail ? new UnsupportedSchemaException("expected") : null);
        }

        TestBackend(Throwable failure) {
            this(failure, "test");
        }

        TestBackend(Throwable failure, String id) {
            this.failure = failure;
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Class<TestCodec> codecType() {
            return TestCodec.class;
        }

        @Override
        public Class<?> lookupHost() {
            return RuntimeCodecRegistryTest.class;
        }

        @Override
        public Emission emit(RuntimeCodecPlan plan, String generatedName) {
            emissions.incrementAndGet();
            assertEquals("getValue", plan.rootStructure().members().getFirst().getter().getName());
            assertEquals("value", plan.rootStructure().members().getFirst().setter().getName());
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            var writer = new ClassWriter(0);
            writer.visit(
                    Opcodes.V17,
                    Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                    generatedName,
                    null,
                    "java/lang/Object",
                    new String[] {Type.getInternalName(TestCodec.class)});
            MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(1, 1);
            constructor.visitEnd();
            RuntimeCodegenBytecode.emitAcceptsBuilder(writer, plan.rootStructure().builderClass());

            MethodVisitor value = writer.visitMethod(
                    Opcodes.ACC_PUBLIC,
                    "value",
                    "()Ljava/lang/String;",
                    null,
                    null);
            value.visitCode();
            value.visitLdcInsn("generated");
            value.visitInsn(Opcodes.ARETURN);
            value.visitMaxs(1, 1);
            value.visitEnd();
            writer.visitEnd();
            return new Emission(writer.toByteArray(), 3);
        }
    }

    public static final class TestShape implements SerializableStruct {
        private final String value;

        TestShape(Builder builder) {
            value = builder.value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SCHEMA.member("value"), value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) value;
        }
    }

    public static final class Builder implements ShapeBuilder<TestShape> {
        private String value;

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder value(Object value) {
            this.value = String.valueOf(value);
            return this;
        }

        @Override
        public TestShape build() {
            return new TestShape(this);
        }

        @Override
        public ShapeBuilder<TestShape> deserialize(ShapeDeserializer decoder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }
    }

    private static final class OtherBuilder implements ShapeBuilder<TestShape> {
        @Override
        public TestShape build() {
            return new TestShape(new Builder());
        }

        @Override
        public ShapeBuilder<TestShape> deserialize(ShapeDeserializer decoder) {
            return this;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }
    }
}
