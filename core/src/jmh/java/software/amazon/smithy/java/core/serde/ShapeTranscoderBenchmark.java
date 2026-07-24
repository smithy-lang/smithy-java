/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;

@State(Scope.Thread)
public class ShapeTranscoderBenchmark {

    private static final int ATTRIBUTE_VALUE_DEPTH = 32;

    private static final Schema SOURCE_SHALLOW =
            Schema.structureBuilder(ShapeId.from("benchmark.source#Shallow"))
                    .putMember("id", PreludeSchemas.STRING)
                    .putMember("count", PreludeSchemas.INTEGER)
                    .putMember("active", PreludeSchemas.BOOLEAN)
                    .build();
    private static final Schema SOURCE_SHALLOW_ID = SOURCE_SHALLOW.member("id");
    private static final Schema SOURCE_SHALLOW_COUNT = SOURCE_SHALLOW.member("count");
    private static final Schema SOURCE_SHALLOW_ACTIVE = SOURCE_SHALLOW.member("active");
    private static final Schema TARGET_SHALLOW =
            Schema.structureBuilder(ShapeId.from("benchmark.target#Shallow"))
                    .putMember("active", PreludeSchemas.BOOLEAN)
                    .putMember("count", PreludeSchemas.LONG)
                    .putMember("id", PreludeSchemas.STRING)
                    .build();

    private static final Schema SOURCE_LABELS = Schema.listBuilder(ShapeId.from("benchmark.source#Labels"))
            .putMember("member", PreludeSchemas.STRING)
            .build();
    private static final Schema SOURCE_LABELS_MEMBER = SOURCE_LABELS.listMember();
    private static final Schema SOURCE_METADATA = Schema.mapBuilder(ShapeId.from("benchmark.source#Metadata"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.STRING)
            .build();
    private static final Schema SOURCE_METADATA_KEY = SOURCE_METADATA.mapKeyMember();
    private static final Schema SOURCE_METADATA_VALUE = SOURCE_METADATA.mapValueMember();
    private static final Schema SOURCE_LEAF = Schema.structureBuilder(ShapeId.from("benchmark.source#Leaf"))
            .putMember("name", PreludeSchemas.STRING)
            .putMember("score", PreludeSchemas.INTEGER)
            .putMember("labels", SOURCE_LABELS)
            .build();
    private static final Schema SOURCE_LEAF_NAME = SOURCE_LEAF.member("name");
    private static final Schema SOURCE_LEAF_SCORE = SOURCE_LEAF.member("score");
    private static final Schema SOURCE_LEAF_LABELS = SOURCE_LEAF.member("labels");
    private static final Schema SOURCE_LEAVES = Schema.listBuilder(ShapeId.from("benchmark.source#Leaves"))
            .putMember("member", SOURCE_LEAF)
            .build();
    private static final Schema SOURCE_LEAVES_MEMBER = SOURCE_LEAVES.listMember();
    private static final Schema SOURCE_BRANCH = Schema.structureBuilder(ShapeId.from("benchmark.source#Branch"))
            .putMember("name", PreludeSchemas.STRING)
            .putMember("leaves", SOURCE_LEAVES)
            .putMember("metadata", SOURCE_METADATA)
            .build();
    private static final Schema SOURCE_BRANCH_NAME = SOURCE_BRANCH.member("name");
    private static final Schema SOURCE_BRANCH_LEAVES = SOURCE_BRANCH.member("leaves");
    private static final Schema SOURCE_BRANCH_METADATA = SOURCE_BRANCH.member("metadata");
    private static final Schema SOURCE_BRANCHES = Schema.listBuilder(ShapeId.from("benchmark.source#Branches"))
            .putMember("member", SOURCE_BRANCH)
            .build();
    private static final Schema SOURCE_BRANCHES_MEMBER = SOURCE_BRANCHES.listMember();
    private static final Schema SOURCE_DEEP = Schema.structureBuilder(ShapeId.from("benchmark.source#Deep"))
            .putMember("requestId", PreludeSchemas.STRING)
            .putMember("primary", SOURCE_BRANCH)
            .putMember("branches", SOURCE_BRANCHES)
            .putMember("metadata", SOURCE_METADATA)
            .build();
    private static final Schema SOURCE_DEEP_REQUEST_ID = SOURCE_DEEP.member("requestId");
    private static final Schema SOURCE_DEEP_PRIMARY = SOURCE_DEEP.member("primary");
    private static final Schema SOURCE_DEEP_BRANCHES = SOURCE_DEEP.member("branches");
    private static final Schema SOURCE_DEEP_METADATA = SOURCE_DEEP.member("metadata");

    private static final Schema TARGET_LABELS = Schema.listBuilder(ShapeId.from("benchmark.target#Labels"))
            .putMember("member", PreludeSchemas.STRING)
            .build();
    private static final Schema TARGET_LABELS_MEMBER = TARGET_LABELS.listMember();
    private static final Schema TARGET_METADATA = Schema.mapBuilder(ShapeId.from("benchmark.target#Metadata"))
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.STRING)
            .build();
    private static final Schema TARGET_METADATA_VALUE = TARGET_METADATA.mapValueMember();
    private static final Schema TARGET_LEAF = Schema.structureBuilder(ShapeId.from("benchmark.target#Leaf"))
            .putMember("labels", TARGET_LABELS)
            .putMember("name", PreludeSchemas.STRING)
            .putMember("score", PreludeSchemas.LONG)
            .build();
    private static final Schema TARGET_LEAVES = Schema.listBuilder(ShapeId.from("benchmark.target#Leaves"))
            .putMember("member", TARGET_LEAF)
            .build();
    private static final Schema TARGET_BRANCH = Schema.structureBuilder(ShapeId.from("benchmark.target#Branch"))
            .putMember("leaves", TARGET_LEAVES)
            .putMember("metadata", TARGET_METADATA)
            .putMember("name", PreludeSchemas.STRING)
            .build();
    private static final Schema TARGET_BRANCHES = Schema.listBuilder(ShapeId.from("benchmark.target#Branches"))
            .putMember("member", TARGET_BRANCH)
            .build();
    private static final Schema TARGET_DEEP = Schema.structureBuilder(ShapeId.from("benchmark.target#Deep"))
            .putMember("branches", TARGET_BRANCHES)
            .putMember("metadata", TARGET_METADATA)
            .putMember("primary", TARGET_BRANCH)
            .putMember("requestId", PreludeSchemas.STRING)
            .build();

    private static final AttributeSchemas SOURCE_ATTRIBUTE_SCHEMAS =
            createAttributeSchemas("benchmark.source", false);
    private static final Schema SOURCE_ATTRIBUTE_VALUE = SOURCE_ATTRIBUTE_SCHEMAS.value();
    private static final Schema SOURCE_ATTRIBUTE_LIST = SOURCE_ATTRIBUTE_SCHEMAS.list();
    private static final Schema SOURCE_ATTRIBUTE_MAP = SOURCE_ATTRIBUTE_SCHEMAS.map();
    private static final Schema SOURCE_ATTRIBUTE_S = SOURCE_ATTRIBUTE_VALUE.member("S");
    private static final Schema SOURCE_ATTRIBUTE_N = SOURCE_ATTRIBUTE_VALUE.member("N");
    private static final Schema SOURCE_ATTRIBUTE_BOOL = SOURCE_ATTRIBUTE_VALUE.member("BOOL");
    private static final Schema SOURCE_ATTRIBUTE_NULL = SOURCE_ATTRIBUTE_VALUE.member("NULL");
    private static final Schema SOURCE_ATTRIBUTE_M = SOURCE_ATTRIBUTE_VALUE.member("M");
    private static final Schema SOURCE_ATTRIBUTE_L = SOURCE_ATTRIBUTE_VALUE.member("L");
    private static final Schema SOURCE_ATTRIBUTE_LIST_MEMBER = SOURCE_ATTRIBUTE_LIST.listMember();
    private static final Schema SOURCE_ATTRIBUTE_MAP_KEY = SOURCE_ATTRIBUTE_MAP.mapKeyMember();
    private static final Schema SOURCE_ATTRIBUTE_MAP_VALUE = SOURCE_ATTRIBUTE_MAP.mapValueMember();
    private static final AttributeSchemas TARGET_ATTRIBUTE_SCHEMAS =
            createAttributeSchemas("benchmark.target", true);
    private static final Schema TARGET_ATTRIBUTE_VALUE = TARGET_ATTRIBUTE_SCHEMAS.value();
    private static final Schema TARGET_ATTRIBUTE_LIST = TARGET_ATTRIBUTE_SCHEMAS.list();
    private static final Schema TARGET_ATTRIBUTE_MAP = TARGET_ATTRIBUTE_SCHEMAS.map();

    private final ShapeTranscoder transcoder = new ShapeTranscoder();
    private SourceShallow shallowSource;
    private SourceDeep deepSource;
    private SourceAttributeValue attributeValueSource;

    @Setup
    public void setup() {
        shallowSource = new SourceShallow("request-id", 42, true);

        var branches = new ArrayList<SourceBranch>(8);
        for (var branchIndex = 0; branchIndex < 8; branchIndex++) {
            var leaves = new ArrayList<SourceLeaf>(8);
            for (var leafIndex = 0; leafIndex < 8; leafIndex++) {
                leaves.add(new SourceLeaf(
                        "leaf-" + branchIndex + '-' + leafIndex,
                        branchIndex * 100 + leafIndex,
                        List.of("red", "green", "blue", "yellow")));
            }

            var metadata = new LinkedHashMap<String, String>(4);
            for (var metadataIndex = 0; metadataIndex < 4; metadataIndex++) {
                metadata.put("branch-key-" + metadataIndex, "branch-value-" + metadataIndex);
            }
            branches.add(new SourceBranch("branch-" + branchIndex, leaves, metadata));
        }

        var metadata = new LinkedHashMap<String, String>(8);
        for (var metadataIndex = 0; metadataIndex < 8; metadataIndex++) {
            metadata.put("root-key-" + metadataIndex, "root-value-" + metadataIndex);
        }
        deepSource = new SourceDeep("request-id", branches.getFirst(), branches, metadata);
        attributeValueSource = createDeepAttributeValue();

        var documentResult = Document.of(attributeValueSource).asShape(new AttributeValueBuilder());
        var transcoderResult = ShapeTranscoder.convert(attributeValueSource, new AttributeValueBuilder());
        if (!documentResult.equals(transcoderResult)) {
            throw new IllegalStateException("AttributeValue benchmark conversions produced different results");
        }
    }

    @Benchmark
    public TargetShallow shallowDocumentIntermediate() {
        return Document.of(shallowSource).asShape(new ShallowBuilder());
    }

    @Benchmark
    public TargetShallow shallowShapeTranscoder() {
        return transcoder.transcode(shallowSource, new ShallowBuilder());
    }

    @Benchmark
    public TargetDeep deepDocumentIntermediate() {
        return Document.of(deepSource).asShape(new DeepBuilder());
    }

    @Benchmark
    public TargetDeep deepShapeTranscoder() {
        return transcoder.transcode(deepSource, new DeepBuilder());
    }

    @Benchmark
    public TargetAttributeValue deepAttributeValueDocumentIntermediate() {
        return Document.of(attributeValueSource).asShape(new AttributeValueBuilder());
    }

    @Benchmark
    public TargetAttributeValue deepAttributeValueShapeTranscoder() {
        return transcoder.transcode(attributeValueSource, new AttributeValueBuilder());
    }

    private record SourceShallow(String id, int count, boolean active) implements SerializableStruct {
        @Override
        public Schema schema() {
            return SOURCE_SHALLOW;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SOURCE_SHALLOW_ID, id);
            serializer.writeInteger(SOURCE_SHALLOW_COUNT, count);
            serializer.writeBoolean(SOURCE_SHALLOW_ACTIVE, active);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private record SourceLeaf(String name, int score, List<String> labels) implements SerializableStruct {
        @Override
        public Schema schema() {
            return SOURCE_LEAF;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SOURCE_LEAF_NAME, name);
            serializer.writeInteger(SOURCE_LEAF_SCORE, score);
            serializer.writeList(SOURCE_LEAF_LABELS, labels, labels.size(), SourceLeaf::writeLabels);
        }

        private static void writeLabels(List<String> labels, ShapeSerializer serializer) {
            for (var label : labels) {
                serializer.writeString(SOURCE_LABELS_MEMBER, label);
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private record SourceBranch(
            String name,
            List<SourceLeaf> leaves,
            Map<String, String> metadata) implements SerializableStruct {
        @Override
        public Schema schema() {
            return SOURCE_BRANCH;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SOURCE_BRANCH_NAME, name);
            serializer.writeList(SOURCE_BRANCH_LEAVES, leaves, leaves.size(), SourceBranch::writeLeaves);
            serializer.writeMap(
                    SOURCE_BRANCH_METADATA,
                    metadata,
                    metadata.size(),
                    SourceBranch::writeMetadata);
        }

        private static void writeLeaves(List<SourceLeaf> leaves, ShapeSerializer serializer) {
            for (var leaf : leaves) {
                serializer.writeStruct(SOURCE_LEAVES_MEMBER, leaf);
            }
        }

        private static void writeMetadata(Map<String, String> metadata, MapSerializer serializer) {
            for (var entry : metadata.entrySet()) {
                serializer.writeEntry(
                        SOURCE_METADATA_KEY,
                        entry.getKey(),
                        entry.getValue(),
                        SourceBranch::writeMetadataValue);
            }
        }

        private static void writeMetadataValue(String value, ShapeSerializer serializer) {
            serializer.writeString(SOURCE_METADATA_VALUE, value);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private record SourceDeep(
            String requestId,
            SourceBranch primary,
            List<SourceBranch> branches,
            Map<String, String> metadata) implements SerializableStruct {
        @Override
        public Schema schema() {
            return SOURCE_DEEP;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SOURCE_DEEP_REQUEST_ID, requestId);
            serializer.writeStruct(SOURCE_DEEP_PRIMARY, primary);
            serializer.writeList(
                    SOURCE_DEEP_BRANCHES,
                    branches,
                    branches.size(),
                    SourceDeep::writeBranches);
            serializer.writeMap(
                    SOURCE_DEEP_METADATA,
                    metadata,
                    metadata.size(),
                    SourceDeep::writeMetadata);
        }

        private static void writeBranches(List<SourceBranch> branches, ShapeSerializer serializer) {
            for (var branch : branches) {
                serializer.writeStruct(SOURCE_BRANCHES_MEMBER, branch);
            }
        }

        private static void writeMetadata(Map<String, String> metadata, MapSerializer serializer) {
            for (var entry : metadata.entrySet()) {
                serializer.writeEntry(
                        SOURCE_METADATA_KEY,
                        entry.getKey(),
                        entry.getValue(),
                        SourceDeep::writeMetadataValue);
            }
        }

        private static void writeMetadataValue(String value, ShapeSerializer serializer) {
            serializer.writeString(SOURCE_METADATA_VALUE, value);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private sealed interface SourceAttributeValue extends SerializableStruct
            permits SourceStringAttribute,
            SourceNumberAttribute,
            SourceBooleanAttribute,
            SourceNullAttribute,
            SourceListAttribute,
            SourceMapAttribute {

        @Override
        default Schema schema() {
            return SOURCE_ATTRIBUTE_VALUE;
        }

        @Override
        default <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    private record SourceStringAttribute(String value) implements SourceAttributeValue {
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SOURCE_ATTRIBUTE_S, value);
        }
    }

    private record SourceNumberAttribute(String value) implements SourceAttributeValue {
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SOURCE_ATTRIBUTE_N, value);
        }
    }

    private record SourceBooleanAttribute(boolean value) implements SourceAttributeValue {
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBoolean(SOURCE_ATTRIBUTE_BOOL, value);
        }
    }

    private record SourceNullAttribute() implements SourceAttributeValue {
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeBoolean(SOURCE_ATTRIBUTE_NULL, true);
        }
    }

    private record SourceListAttribute(List<SourceAttributeValue> value) implements SourceAttributeValue {
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeList(SOURCE_ATTRIBUTE_L, value, value.size(), SourceListAttribute::write);
        }

        private static void write(List<SourceAttributeValue> values, ShapeSerializer serializer) {
            for (var value : values) {
                serializer.writeStruct(SOURCE_ATTRIBUTE_LIST_MEMBER, value);
            }
        }
    }

    private record SourceMapAttribute(Map<String, SourceAttributeValue> value) implements SourceAttributeValue {
        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeMap(SOURCE_ATTRIBUTE_M, value, value.size(), SourceMapAttribute::write);
        }

        private static void write(Map<String, SourceAttributeValue> values, MapSerializer serializer) {
            for (var entry : values.entrySet()) {
                serializer.writeEntry(
                        SOURCE_ATTRIBUTE_MAP_KEY,
                        entry.getKey(),
                        entry.getValue(),
                        SourceMapAttribute::writeValue);
            }
        }

        private static void writeValue(SourceAttributeValue value, ShapeSerializer serializer) {
            serializer.writeStruct(SOURCE_ATTRIBUTE_MAP_VALUE, value);
        }
    }

    public record TargetShallow(String id, long count, boolean active) implements SerializableShape {
        @Override
        public void serialize(ShapeSerializer encoder) {
            throw new UnsupportedOperationException();
        }
    }

    public record TargetLeaf(String name, long score, List<String> labels) {}

    public record TargetBranch(String name, List<TargetLeaf> leaves, Map<String, String> metadata) {}

    public record TargetDeep(
            String requestId,
            TargetBranch primary,
            List<TargetBranch> branches,
            Map<String, String> metadata) implements SerializableShape {
        @Override
        public void serialize(ShapeSerializer encoder) {
            throw new UnsupportedOperationException();
        }
    }

    public sealed interface TargetAttributeValue extends SerializableShape
            permits TargetStringAttribute,
            TargetNumberAttribute,
            TargetBooleanAttribute,
            TargetNullAttribute,
            TargetListAttribute,
            TargetMapAttribute {

        @Override
        default void serialize(ShapeSerializer encoder) {
            throw new UnsupportedOperationException();
        }
    }

    public record TargetStringAttribute(String value) implements TargetAttributeValue {}

    public record TargetNumberAttribute(String value) implements TargetAttributeValue {}

    public record TargetBooleanAttribute(boolean value) implements TargetAttributeValue {}

    public record TargetNullAttribute() implements TargetAttributeValue {}

    public record TargetListAttribute(List<TargetAttributeValue> value) implements TargetAttributeValue {}

    public record TargetMapAttribute(Map<String, TargetAttributeValue> value) implements TargetAttributeValue {}

    private static final class ShallowBuilder implements ShapeBuilder<TargetShallow> {
        private String id;
        private long count;
        private boolean active;

        @Override
        public TargetShallow build() {
            return new TargetShallow(id, count, active);
        }

        @Override
        public ShapeBuilder<TargetShallow> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(TARGET_SHALLOW, this, (builder, member, value) -> {
                switch (member.memberIndex()) {
                    case 0 -> builder.active = value.readBoolean(member);
                    case 1 -> builder.count = value.readLong(member);
                    case 2 -> builder.id = value.readString(member);
                    default -> throw new IllegalArgumentException("Unexpected member " + member);
                }
            });
            return this;
        }

        @Override
        public Schema schema() {
            return TARGET_SHALLOW;
        }
    }

    private static final class DeepBuilder implements ShapeBuilder<TargetDeep> {
        private String requestId;
        private TargetBranch primary;
        private List<TargetBranch> branches;
        private Map<String, String> metadata;

        @Override
        public TargetDeep build() {
            return new TargetDeep(requestId, primary, branches, metadata);
        }

        @Override
        public ShapeBuilder<TargetDeep> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(TARGET_DEEP, this, (builder, member, value) -> {
                switch (member.memberIndex()) {
                    case 0 -> builder.branches = readBranches(value);
                    case 1 -> builder.metadata = readMetadata(value);
                    case 2 -> builder.primary = readBranch(value);
                    case 3 -> builder.requestId = value.readString(member);
                    default -> throw new IllegalArgumentException("Unexpected member " + member);
                }
            });
            return this;
        }

        private static TargetBranch readBranch(ShapeDeserializer deserializer) {
            var state = new BranchState();
            deserializer.readStruct(TARGET_BRANCH, state, (branch, member, value) -> {
                switch (member.memberIndex()) {
                    case 0 -> branch.leaves = readLeaves(value);
                    case 1 -> branch.metadata = readMetadata(value);
                    case 2 -> branch.name = value.readString(member);
                    default -> throw new IllegalArgumentException("Unexpected member " + member);
                }
            });
            return new TargetBranch(state.name, state.leaves, state.metadata);
        }

        private static List<TargetBranch> readBranches(ShapeDeserializer deserializer) {
            List<TargetBranch> result = newList(deserializer);
            deserializer.readList(TARGET_BRANCHES, result, (branches, value) -> branches.add(readBranch(value)));
            return result;
        }

        private static List<TargetLeaf> readLeaves(ShapeDeserializer deserializer) {
            List<TargetLeaf> result = newList(deserializer);
            deserializer.readList(TARGET_LEAVES, result, (leaves, value) -> leaves.add(readLeaf(value)));
            return result;
        }

        private static TargetLeaf readLeaf(ShapeDeserializer deserializer) {
            var state = new LeafState();
            deserializer.readStruct(TARGET_LEAF, state, (leaf, member, value) -> {
                switch (member.memberIndex()) {
                    case 0 -> leaf.labels = readLabels(value);
                    case 1 -> leaf.name = value.readString(member);
                    case 2 -> leaf.score = value.readLong(member);
                    default -> throw new IllegalArgumentException("Unexpected member " + member);
                }
            });
            return new TargetLeaf(state.name, state.score, state.labels);
        }

        private static List<String> readLabels(ShapeDeserializer deserializer) {
            List<String> result = newList(deserializer);
            deserializer.readList(
                    TARGET_LABELS,
                    result,
                    (labels, value) -> labels.add(value.readString(TARGET_LABELS_MEMBER)));
            return result;
        }

        private static Map<String, String> readMetadata(ShapeDeserializer deserializer) {
            var size = containerSize(deserializer);
            Map<String, String> result =
                    size == -1 ? new LinkedHashMap<>() : LinkedHashMap.newLinkedHashMap(size);
            deserializer.readStringMap(
                    TARGET_METADATA,
                    result,
                    (metadata, key, value) -> metadata.put(
                            key,
                            value.readString(TARGET_METADATA_VALUE)));
            return result;
        }

        private static <T> List<T> newList(ShapeDeserializer deserializer) {
            var size = containerSize(deserializer);
            return size == -1 ? new ArrayList<>() : new ArrayList<>(size);
        }

        private static int containerSize(ShapeDeserializer deserializer) {
            return Math.min(deserializer.containerSize(), deserializer.containerPreAllocationLimit());
        }

        @Override
        public Schema schema() {
            return TARGET_DEEP;
        }
    }

    private static final class AttributeValueBuilder implements ShapeBuilder<TargetAttributeValue> {
        private TargetAttributeValue result;

        @Override
        public TargetAttributeValue build() {
            return result;
        }

        @Override
        public ShapeBuilder<TargetAttributeValue> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(TARGET_ATTRIBUTE_VALUE, this, (builder, member, value) -> {
                builder.result = switch (member.memberIndex()) {
                    case 0 -> new TargetListAttribute(readAttributeList(value));
                    case 1 -> new TargetMapAttribute(readAttributeMap(value));
                    case 2 -> {
                        value.readBoolean(member);
                        yield new TargetNullAttribute();
                    }
                    case 3 -> new TargetBooleanAttribute(value.readBoolean(member));
                    case 4 -> new TargetNumberAttribute(value.readString(member));
                    case 5 -> new TargetStringAttribute(value.readString(member));
                    default -> throw new IllegalArgumentException("Unexpected member " + member);
                };
            });
            return this;
        }

        private static List<TargetAttributeValue> readAttributeList(ShapeDeserializer deserializer) {
            List<TargetAttributeValue> result = DeepBuilder.newList(deserializer);
            deserializer.readList(
                    TARGET_ATTRIBUTE_LIST,
                    result,
                    (values, value) -> values.add(new AttributeValueBuilder().deserialize(value).build()));
            return result;
        }

        private static Map<String, TargetAttributeValue> readAttributeMap(ShapeDeserializer deserializer) {
            var size = DeepBuilder.containerSize(deserializer);
            Map<String, TargetAttributeValue> result =
                    size == -1 ? new LinkedHashMap<>() : LinkedHashMap.newLinkedHashMap(size);
            deserializer.readStringMap(
                    TARGET_ATTRIBUTE_MAP,
                    result,
                    (values, key, value) -> values.put(
                            key,
                            new AttributeValueBuilder().deserialize(value).build()));
            return result;
        }

        @Override
        public Schema schema() {
            return TARGET_ATTRIBUTE_VALUE;
        }
    }

    private static AttributeSchemas createAttributeSchemas(String namespace, boolean reverseMemberOrder) {
        var valueBuilder = Schema.unionBuilder(ShapeId.from(namespace + "#AttributeValue"));
        var listBuilder = Schema.listBuilder(ShapeId.from(namespace + "#AttributeValueList"))
                .putMember("member", valueBuilder);
        var mapBuilder = Schema.mapBuilder(ShapeId.from(namespace + "#AttributeValueMap"))
                .putMember("key", PreludeSchemas.STRING)
                .putMember("value", valueBuilder);

        if (reverseMemberOrder) {
            valueBuilder
                    .putMember("L", listBuilder)
                    .putMember("M", mapBuilder)
                    .putMember("NULL", PreludeSchemas.BOOLEAN)
                    .putMember("BOOL", PreludeSchemas.BOOLEAN)
                    .putMember("N", PreludeSchemas.STRING)
                    .putMember("S", PreludeSchemas.STRING);
        } else {
            valueBuilder
                    .putMember("S", PreludeSchemas.STRING)
                    .putMember("N", PreludeSchemas.STRING)
                    .putMember("BOOL", PreludeSchemas.BOOLEAN)
                    .putMember("NULL", PreludeSchemas.BOOLEAN)
                    .putMember("M", mapBuilder)
                    .putMember("L", listBuilder);
        }

        var value = valueBuilder.build().resolve();
        var list = listBuilder.build().resolve();
        var map = mapBuilder.build().resolve();
        return new AttributeSchemas(value, list, map);
    }

    private static SourceAttributeValue createDeepAttributeValue() {
        SourceAttributeValue value = new SourceStringAttribute("terminal");
        for (var depth = 0; depth < ATTRIBUTE_VALUE_DEPTH; depth++) {
            if ((depth & 1) == 0) {
                var entries = LinkedHashMap.<String, SourceAttributeValue>newLinkedHashMap(4);
                entries.put("name", new SourceStringAttribute("level-" + depth));
                entries.put("count", new SourceNumberAttribute(Integer.toString(depth)));
                entries.put("active", new SourceBooleanAttribute((depth & 2) == 0));
                entries.put("next", value);
                value = new SourceMapAttribute(entries);
            } else {
                value = new SourceListAttribute(List.of(
                        new SourceStringAttribute("level-" + depth),
                        new SourceNumberAttribute(Integer.toString(depth)),
                        new SourceNullAttribute(),
                        value));
            }
        }
        return value;
    }

    private record AttributeSchemas(Schema value, Schema list, Schema map) {}

    private static final class BranchState {
        private String name;
        private List<TargetLeaf> leaves;
        private Map<String, String> metadata;
    }

    private static final class LeafState {
        private String name;
        private long score;
        private List<String> labels;
    }
}
