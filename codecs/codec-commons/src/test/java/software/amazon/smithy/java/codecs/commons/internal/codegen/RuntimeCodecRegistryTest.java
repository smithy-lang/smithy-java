/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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

final class RuntimeCodecRegistryTest {
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
        assertEquals(1, backend.emissions.get());
        assertEquals(1, registry.diagnostics().snapshot().successes());
        assertEquals(1, registry.diagnostics().snapshot().emittedClasses());
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
        assertEquals(1, registry.size());
    }

    @Test
    void cachesGenerationFailure() {
        var backend = new TestBackend(true);
        var registry = new RuntimeCodecRegistry<>(backend);

        assertNull(registry.get(SCHEMA, "settings"));
        assertNull(registry.get(SCHEMA, "settings"));

        assertEquals(1, backend.emissions.get());
        assertEquals(1, registry.diagnostics().snapshot().failures());
        assertEquals(2, registry.diagnostics().snapshot().fallbacks());
    }

    @Test
    void evictsCompletedEntries() {
        var backend = new TestBackend(false);
        var registry = new RuntimeCodecRegistry<>(backend, 1);

        assertNotNull(registry.get(SCHEMA, "one"));
        assertNotNull(registry.get(SCHEMA, "two"));

        assertEquals(1, registry.size());
        assertEquals(1, registry.diagnostics().snapshot().evictions());
    }

    private interface TestCodec {
        String value();
    }

    private static final class TestBackend implements RuntimeCodecBackend<TestCodec> {
        private final AtomicInteger emissions = new AtomicInteger();
        private final boolean fail;

        TestBackend(boolean fail) {
            this.fail = fail;
        }

        @Override
        public String id() {
            return "test";
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
            if (fail) {
                throw new UnsupportedSchemaException("expected");
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
            return new Emission(writer.toByteArray(), 2);
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
}
