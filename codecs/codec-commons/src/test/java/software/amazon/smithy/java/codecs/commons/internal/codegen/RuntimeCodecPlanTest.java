/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public final class RuntimeCodecPlanTest {

    private static final Schema NESTED = Schema.structureBuilder(ShapeId.from("example#Nested"))
            .shapeClass(Nested.class)
            .builderSupplier(Nested.Builder::new)
            .putMember("a", PreludeSchemas.STRING)
            .putMember("b", PreludeSchemas.STRING)
            .build();

    private static final Schema ROOT = Schema.structureBuilder(ShapeId.from("example#Root"))
            .shapeClass(Root.class)
            .builderSupplier(Root.Builder::new)
            .putMember("kept", PreludeSchemas.STRING)
            .putMember("dropped", NESTED)
            .putMember("nested", NESTED)
            .build();

    private static final Schema NO_SETTER = Schema.structureBuilder(ShapeId.from("example#NoSetter"))
            .shapeClass(NoSetter.class)
            .builderSupplier(NoSetter.Builder::new)
            .putMember("value", PreludeSchemas.STRING)
            .build();

    private static final Schema TAGS = Schema.listBuilder(ShapeId.from("example#Tags"))
            .putMember("member", PreludeSchemas.STRING)
            .build();

    private static final Schema ACRONYMS = Schema.structureBuilder(ShapeId.from("example#Acronyms"))
            .shapeClass(Acronyms.class)
            .builderSupplier(Acronyms.Builder::new)
            .putMember("ACL", PreludeSchemas.STRING)
            .putMember("SSEKMSKeyId", PreludeSchemas.STRING)
            .putMember("GrantReadACP", PreludeSchemas.STRING)
            .putMember("ETag", PreludeSchemas.STRING)
            .putMember("BucketKeyEnabled", PreludeSchemas.BOOLEAN)
            .putMember("Tags", TAGS)
            .build();

    private static final Schema SHADOWS_SCHEMA = Schema.structureBuilder(ShapeId.from("example#ShadowsSchema"))
            .shapeClass(ShadowsSchema.class)
            .builderSupplier(ShadowsSchema.Builder::new)
            .putMember("schema", PreludeSchemas.STRING)
            .build();

    private static final Schema SHADOWS_OBJECT = Schema.structureBuilder(ShapeId.from("example#ShadowsObject"))
            .shapeClass(ShadowsObject.class)
            .builderSupplier(ShadowsObject.Builder::new)
            .putMember("hashCode", PreludeSchemas.STRING)
            .build();

    private static final Schema RECURSIVE = recursiveSchema();

    private static final Schema WRONG_ACCESSOR = Schema.structureBuilder(ShapeId.from("example#WrongAccessor"))
            .shapeClass(WrongAccessor.class)
            .builderSupplier(WrongAccessor.Builder::new)
            .putMember("value", PreludeSchemas.STRING)
            .build();

    private static final Schema PACKAGE_PRIVATE_SHAPE =
            Schema.structureBuilder(ShapeId.from("example#PackagePrivateShape"))
                    .shapeClass(PackagePrivateShape.class)
                    .builderSupplier(PackagePrivateShape.Builder::new)
                    .putMember("value", PreludeSchemas.STRING)
                    .build();

    private static final Schema PACKAGE_PRIVATE_BUILDER =
            Schema.structureBuilder(ShapeId.from("example#PackagePrivateBuilder"))
                    .shapeClass(PackagePrivateBuilder.class)
                    .builderSupplier(PackagePrivateBuilder.Builder::new)
                    .putMember("value", PreludeSchemas.STRING)
                    .build();

    private static final Schema PUBLIC_SHAPE_IN_PACKAGE_PRIVATE_OUTER =
            Schema.structureBuilder(ShapeId.from("example#PublicShapeInPackagePrivateOuter"))
                    .shapeClass(PackagePrivateOuter.PublicShape.class)
                    .builderSupplier(PackagePrivateOuter.PublicShape.Builder::new)
                    .putMember("value", PreludeSchemas.STRING)
                    .build();

    private static final Schema COLLIDING_ACCESSORS =
            Schema.structureBuilder(ShapeId.from("example#CollidingAccessors"))
                    .shapeClass(CollidingAccessors.class)
                    .builderSupplier(CollidingAccessors.Builder::new)
                    .putMember("GrantReadACP", PreludeSchemas.STRING)
                    .putMember("grantReadacP", PreludeSchemas.STRING)
                    .build();

    private static Schema recursiveSchema() {
        var builder = Schema.structureBuilder(ShapeId.from("example#Recursive"))
                .shapeClass(Recursive.class)
                .builderSupplier(Recursive.Builder::new)
                .putMember("value", PreludeSchemas.STRING);
        return builder.putMember("next", builder).build();
    }

    private static List<String> memberNames(RuntimeCodecPlan.StructPlan plan) {
        return plan.members().stream().map(RuntimeCodecPlan.MemberPlan::memberName).toList();
    }

    @Test
    void selectsAllRootMembersByDefault() {
        var plan = RuntimeCodecPlan.analyze(ROOT);

        assertEquals(List.of("kept", "dropped", "nested"), memberNames(plan.rootStructure()));
    }

    @Test
    void memberSelectorNarrowsTheRoot() {
        var plan = RuntimeCodecPlan.analyze(
                ROOT,
                RuntimeCodecBackend.Budgets.unbounded(),
                RuntimeCodecBackend.Mode.READ_WRITE,
                (root, member) -> !member.memberName().equals("dropped"));

        assertEquals(List.of("kept", "nested"), memberNames(plan.rootStructure()));
    }

    @Test
    void memberSelectorDoesNotNarrowNestedShapes() {
        var plan = RuntimeCodecPlan.analyze(
                ROOT,
                RuntimeCodecBackend.Budgets.unbounded(),
                RuntimeCodecBackend.Mode.READ_WRITE,
                (root, member) -> !member.memberName().equals("a"));

        var nested = plan.structures()
                .stream()
                .filter(s -> s.schema().id().equals(NESTED.id()))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("a", "b"), memberNames(nested));
    }

    @Test
    void excludingAMemberAlsoSkipsItsUnreachableTarget() {
        var keepsNested = RuntimeCodecPlan.analyze(
                ROOT,
                RuntimeCodecBackend.Budgets.unbounded(),
                RuntimeCodecBackend.Mode.READ_WRITE,
                (root, member) -> !member.memberName().equals("dropped"));
        assertTrue(keepsNested.structures().stream().anyMatch(s -> s.schema().id().equals(NESTED.id())));

        var dropsNested = RuntimeCodecPlan.analyze(
                ROOT,
                RuntimeCodecBackend.Budgets.unbounded(),
                RuntimeCodecBackend.Mode.READ_WRITE,
                (root, member) -> member.memberName().equals("kept"));

        assertEquals(List.of("kept"), memberNames(dropsNested.rootStructure()));
        assertTrue(dropsNested.structures().stream().noneMatch(s -> s.schema().id().equals(NESTED.id())));
    }

    @Test
    void readWriteModeRejectsAShapeWithNoResolvableSetter() {
        assertThrows(UnsupportedSchemaException.class, () -> RuntimeCodecPlan.analyze(NO_SETTER));
    }

    @Test
    void writeOnlyModeSkipsSetterAndBuilderResolution() {
        var plan = RuntimeCodecPlan.analyze(
                NO_SETTER,
                RuntimeCodecBackend.Budgets.unbounded(),
                RuntimeCodecBackend.Mode.WRITE_ONLY,
                RuntimeCodecBackend.MemberSelector.all());

        var structure = plan.rootStructure();
        assertNull(structure.builderClass());
        assertNull(structure.builderFactory());
        assertNull(structure.members().getFirst().setter());
        assertNotNull(structure.members().getFirst().getter());
        assertEquals("getValue", structure.members().getFirst().getter().getName());
    }

    @Test
    void resolvesAccessorsThatFoldAcronyms() {
        var plan = RuntimeCodecPlan.analyze(ACRONYMS);
        var members = plan.rootStructure().members();

        assertEquals(
                List.of("getAcl", "getSsekmsKeyId", "getGrantReadacP", "getETag", "isBucketKeyEnabled", "getTags"),
                members.stream().map(m -> m.getter().getName()).toList());
        assertEquals(
                List.of("acl", "ssekmsKeyId", "grantReadacP", "eTag", "bucketKeyEnabled", "tags"),
                members.stream().map(m -> m.setter().getName()).toList());
        assertEquals("hasTags", members.get(5).presence().getName());
        assertNull(members.getFirst().presence());
    }

    @Test
    void doesNotResolveAMemberToAFrameworkOrObjectMethod() {
        assertThrows(UnsupportedSchemaException.class, () -> RuntimeCodecPlan.analyze(SHADOWS_SCHEMA));
        assertThrows(UnsupportedSchemaException.class, () -> RuntimeCodecPlan.analyze(SHADOWS_OBJECT));
    }

    @Test
    void rejectsNarrowedRecursiveRoot() {
        assertThrows(
                UnsupportedSchemaException.class,
                () -> RuntimeCodecPlan.analyze(
                        RECURSIVE,
                        RuntimeCodecBackend.Budgets.unbounded(),
                        RuntimeCodecBackend.Mode.READ_WRITE,
                        (root, member) -> member.memberName().equals("next")));

        var nonRecursiveSubset = RuntimeCodecPlan.analyze(
                RECURSIVE,
                RuntimeCodecBackend.Budgets.unbounded(),
                RuntimeCodecBackend.Mode.READ_WRITE,
                (root, member) -> member.memberName().equals("value"));
        assertEquals(List.of("value"), memberNames(nonRecursiveSubset.rootStructure()));
    }

    @Test
    void rejectsAccessorTypeThatDoesNotMatchSchema() {
        assertThrows(UnsupportedSchemaException.class, () -> RuntimeCodecPlan.analyze(WRONG_ACCESSOR));
    }

    @Test
    void rejectsPackagePrivateShapeClass() {
        assertThrows(UnsupportedSchemaException.class, () -> RuntimeCodecPlan.analyze(PACKAGE_PRIVATE_SHAPE));
    }

    @Test
    void rejectsPackagePrivateBuilderClass() {
        assertThrows(UnsupportedSchemaException.class, () -> RuntimeCodecPlan.analyze(PACKAGE_PRIVATE_BUILDER));
    }

    @Test
    void rejectsPublicShapeNestedInPackagePrivateClass() {
        assertThrows(
                UnsupportedSchemaException.class,
                () -> RuntimeCodecPlan.analyze(PUBLIC_SHAPE_IN_PACKAGE_PRIVATE_OUTER));
    }

    @Test
    void rejectsMembersThatResolveToTheSameAccessor() {
        assertThrows(UnsupportedSchemaException.class, () -> RuntimeCodecPlan.analyze(COLLIDING_ACCESSORS));
    }

    @Test
    void chunksAtExactMemberBoundaries() {
        var plan = RuntimeCodecPlan.analyze(
                ACRONYMS,
                new RuntimeCodecBackend.Budgets(
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        2,
                        2));
        var structure = plan.rootStructure();

        assertEquals(
                List.of(
                        new RuntimeCodecPlan.MethodRange(0, 2, 120),
                        new RuntimeCodecPlan.MethodRange(2, 4, 120),
                        new RuntimeCodecPlan.MethodRange(4, 6, 96)),
                structure.writerChunks());
        assertEquals(3, structure.readerBuckets());
    }

    public static final class Root implements SerializableStruct {
        private final String kept;
        private final Nested dropped;
        private final Nested nested;

        Root(Builder builder) {
            this.kept = builder.kept;
            this.dropped = builder.dropped;
            this.nested = builder.nested;
        }

        public String getKept() {
            return kept;
        }

        public Nested getDropped() {
            return dropped;
        }

        public Nested getNested() {
            return nested;
        }

        @Override
        public Schema schema() {
            return ROOT;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(ROOT.member("kept"), kept);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) kept;
        }

        public static final class Builder implements ShapeBuilder<Root> {
            private String kept;
            private Nested dropped;
            private Nested nested;

            public Builder kept(String kept) {
                this.kept = kept;
                return this;
            }

            public Builder dropped(Nested dropped) {
                this.dropped = dropped;
                return this;
            }

            public Builder nested(Nested nested) {
                this.nested = nested;
                return this;
            }

            @Override
            public Root build() {
                return new Root(this);
            }

            @Override
            public ShapeBuilder<Root> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return ROOT;
            }
        }
    }

    public static final class Nested implements SerializableStruct {
        private final String a;
        private final String b;

        Nested(Builder builder) {
            this.a = builder.a;
            this.b = builder.b;
        }

        public String getA() {
            return a;
        }

        public String getB() {
            return b;
        }

        @Override
        public Schema schema() {
            return NESTED;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(NESTED.member("a"), a);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) a;
        }

        public static final class Builder implements ShapeBuilder<Nested> {
            private String a;
            private String b;

            public Builder a(String a) {
                this.a = a;
                return this;
            }

            public Builder b(String b) {
                this.b = b;
                return this;
            }

            @Override
            public Nested build() {
                return new Nested(this);
            }

            @Override
            public ShapeBuilder<Nested> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return NESTED;
            }
        }
    }

    public static final class Acronyms implements SerializableStruct {
        private final String acl;
        private final String ssekmsKeyId;
        private final String grantReadacP;
        private final String eTag;
        private final Boolean bucketKeyEnabled;
        private final List<String> tags;

        Acronyms(Builder builder) {
            this.acl = builder.acl;
            this.ssekmsKeyId = builder.ssekmsKeyId;
            this.grantReadacP = builder.grantReadacP;
            this.eTag = builder.eTag;
            this.bucketKeyEnabled = builder.bucketKeyEnabled;
            this.tags = builder.tags;
        }

        public String getAcl() {
            return acl;
        }

        public String getSsekmsKeyId() {
            return ssekmsKeyId;
        }

        public String getGrantReadacP() {
            return grantReadacP;
        }

        public String getETag() {
            return eTag;
        }

        public Boolean isBucketKeyEnabled() {
            return bucketKeyEnabled;
        }

        public List<String> getTags() {
            return tags;
        }

        public boolean hasTags() {
            return tags != null;
        }

        @Override
        public Schema schema() {
            return ACRONYMS;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(ACRONYMS.member("ACL"), acl);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) acl;
        }

        public static final class Builder implements ShapeBuilder<Acronyms> {
            private String acl;
            private String ssekmsKeyId;
            private String grantReadacP;
            private String eTag;
            private Boolean bucketKeyEnabled;
            private List<String> tags;

            public Builder acl(String acl) {
                this.acl = acl;
                return this;
            }

            public Builder ssekmsKeyId(String ssekmsKeyId) {
                this.ssekmsKeyId = ssekmsKeyId;
                return this;
            }

            public Builder grantReadacP(String grantReadacP) {
                this.grantReadacP = grantReadacP;
                return this;
            }

            public Builder eTag(String eTag) {
                this.eTag = eTag;
                return this;
            }

            public Builder bucketKeyEnabled(Boolean bucketKeyEnabled) {
                this.bucketKeyEnabled = bucketKeyEnabled;
                return this;
            }

            public Builder tags(List<String> tags) {
                this.tags = tags;
                return this;
            }

            @Override
            public Acronyms build() {
                return new Acronyms(this);
            }

            @Override
            public ShapeBuilder<Acronyms> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return ACRONYMS;
            }
        }
    }

    public static final class ShadowsSchema implements SerializableStruct {
        @Override
        public Schema schema() {
            return SHADOWS_SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        public static final class Builder implements ShapeBuilder<ShadowsSchema> {
            @Override
            public ShadowsSchema build() {
                return new ShadowsSchema();
            }

            @Override
            public ShapeBuilder<ShadowsSchema> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return SHADOWS_SCHEMA;
            }
        }
    }

    public static final class ShadowsObject implements SerializableStruct {
        @Override
        public Schema schema() {
            return SHADOWS_OBJECT;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        public static final class Builder implements ShapeBuilder<ShadowsObject> {
            @Override
            public ShadowsObject build() {
                return new ShadowsObject();
            }

            @Override
            public ShapeBuilder<ShadowsObject> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return SHADOWS_OBJECT;
            }
        }
    }

    public static final class Recursive implements SerializableStruct {
        private final String value;
        private final Recursive next;

        Recursive(Builder builder) {
            this.value = builder.value;
            this.next = builder.next;
        }

        public String getValue() {
            return value;
        }

        public Recursive getNext() {
            return next;
        }

        @Override
        public Schema schema() {
            return RECURSIVE;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        public static final class Builder implements ShapeBuilder<Recursive> {
            private String value;
            private Recursive next;

            public Builder value(String value) {
                this.value = value;
                return this;
            }

            public Builder next(Recursive next) {
                this.next = next;
                return this;
            }

            @Override
            public Recursive build() {
                return new Recursive(this);
            }

            @Override
            public ShapeBuilder<Recursive> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return RECURSIVE;
            }
        }
    }

    public static final class WrongAccessor implements SerializableStruct {
        private final CharSequence value;

        WrongAccessor(Builder builder) {
            this.value = builder.value;
        }

        public CharSequence getValue() {
            return value;
        }

        @Override
        public Schema schema() {
            return WRONG_ACCESSOR;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        public static final class Builder implements ShapeBuilder<WrongAccessor> {
            private CharSequence value;

            public Builder value(CharSequence value) {
                this.value = value;
                return this;
            }

            @Override
            public WrongAccessor build() {
                return new WrongAccessor(this);
            }

            @Override
            public ShapeBuilder<WrongAccessor> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return WRONG_ACCESSOR;
            }
        }
    }

    static final class PackagePrivateShape implements SerializableStruct {
        private final String value;

        PackagePrivateShape(Builder builder) {
            this.value = builder.value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public Schema schema() {
            return PACKAGE_PRIVATE_SHAPE;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        public static final class Builder implements ShapeBuilder<PackagePrivateShape> {
            private String value;

            public Builder value(String value) {
                this.value = value;
                return this;
            }

            @Override
            public PackagePrivateShape build() {
                return new PackagePrivateShape(this);
            }

            @Override
            public ShapeBuilder<PackagePrivateShape> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return PACKAGE_PRIVATE_SHAPE;
            }
        }
    }

    public static final class PackagePrivateBuilder implements SerializableStruct {
        private final String value;

        PackagePrivateBuilder(Builder builder) {
            this.value = builder.value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public Schema schema() {
            return PACKAGE_PRIVATE_BUILDER;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        static final class Builder implements ShapeBuilder<PackagePrivateBuilder> {
            private String value;

            public Builder value(String value) {
                this.value = value;
                return this;
            }

            @Override
            public PackagePrivateBuilder build() {
                return new PackagePrivateBuilder(this);
            }

            @Override
            public ShapeBuilder<PackagePrivateBuilder> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return PACKAGE_PRIVATE_BUILDER;
            }
        }
    }

    static final class PackagePrivateOuter {
        public static final class PublicShape implements SerializableStruct {
            private final String value;

            PublicShape(Builder builder) {
                this.value = builder.value;
            }

            public String getValue() {
                return value;
            }

            @Override
            public Schema schema() {
                return PUBLIC_SHAPE_IN_PACKAGE_PRIVATE_OUTER;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {}

            @Override
            public <T> T getMemberValue(Schema member) {
                return null;
            }

            public static final class Builder implements ShapeBuilder<PublicShape> {
                private String value;

                public Builder value(String value) {
                    this.value = value;
                    return this;
                }

                @Override
                public PublicShape build() {
                    return new PublicShape(this);
                }

                @Override
                public ShapeBuilder<PublicShape> deserialize(ShapeDeserializer decoder) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Schema schema() {
                    return PUBLIC_SHAPE_IN_PACKAGE_PRIVATE_OUTER;
                }
            }
        }
    }

    public static final class CollidingAccessors implements SerializableStruct {
        private final String value;

        CollidingAccessors(Builder builder) {
            this.value = builder.value;
        }

        public String getGrantReadacP() {
            return value;
        }

        @Override
        public Schema schema() {
            return COLLIDING_ACCESSORS;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        public static final class Builder implements ShapeBuilder<CollidingAccessors> {
            private String value;

            public Builder grantReadacP(String value) {
                this.value = value;
                return this;
            }

            @Override
            public CollidingAccessors build() {
                return new CollidingAccessors(this);
            }

            @Override
            public ShapeBuilder<CollidingAccessors> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return COLLIDING_ACCESSORS;
            }
        }
    }

    public static final class NoSetter implements SerializableStruct {
        private final String value;

        NoSetter(Builder builder) {
            this.value = builder.value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public Schema schema() {
            return NO_SETTER;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(NO_SETTER.member("value"), value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) value;
        }

        public static final class Builder implements ShapeBuilder<NoSetter> {
            private final String value = null;

            @Override
            public NoSetter build() {
                return new NoSetter(this);
            }

            @Override
            public ShapeBuilder<NoSetter> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return NO_SETTER;
            }
        }
    }
}
