

package software.amazon.smithy.java.codegen.test.model;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SetsAllTypesInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.lists#SetsAllTypesInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("setOfBoolean", SharedSchemas.SET_OF_BOOLEANS)
        .putMember("setOfNumber", SharedSchemas.SET_OF_NUMBER)
        .putMember("setOfString", SharedSchemas.SET_OF_STRINGS)
        .putMember("setOfBlobs", SharedSchemas.SET_OF_BLOBS)
        .putMember("setOfTimestamps", SharedSchemas.SET_OF_TIMESTAMPS)
        .putMember("setOfUnion", SharedSchemas.SET_OF_UNIONS)
        .putMember("setOfEnum", SharedSchemas.SET_OF_ENUMS)
        .putMember("setOfIntEnum", SharedSchemas.SET_OF_INT_ENUMS)
        .putMember("setOfStruct", SharedSchemas.SET_OF_STRUCTS)
        .putMember("setOfStringList", SharedSchemas.SET_OF_STRING_LIST)
        .putMember("setOfStringMap", SharedSchemas.SET_OF_STRING_MAP)
        .build();

    private static final Schema SCHEMA_SET_OF_BOOLEAN = SCHEMA.member("setOfBoolean");
    private static final Schema SCHEMA_SET_OF_NUMBER = SCHEMA.member("setOfNumber");
    private static final Schema SCHEMA_SET_OF_STRING = SCHEMA.member("setOfString");
    private static final Schema SCHEMA_SET_OF_BLOBS = SCHEMA.member("setOfBlobs");
    private static final Schema SCHEMA_SET_OF_TIMESTAMPS = SCHEMA.member("setOfTimestamps");
    private static final Schema SCHEMA_SET_OF_UNION = SCHEMA.member("setOfUnion");
    private static final Schema SCHEMA_SET_OF_ENUM = SCHEMA.member("setOfEnum");
    private static final Schema SCHEMA_SET_OF_INT_ENUM = SCHEMA.member("setOfIntEnum");
    private static final Schema SCHEMA_SET_OF_STRUCT = SCHEMA.member("setOfStruct");
    private static final Schema SCHEMA_SET_OF_STRING_LIST = SCHEMA.member("setOfStringList");
    private static final Schema SCHEMA_SET_OF_STRING_MAP = SCHEMA.member("setOfStringMap");

    private transient final Set<Boolean> setOfBoolean;
    private transient final Set<Integer> setOfNumber;
    private transient final Set<String> setOfString;
    private transient final Set<ByteBuffer> setOfBlobs;
    private transient final Set<Instant> setOfTimestamps;
    private transient final Set<NestedUnion> setOfUnion;
    private transient final Set<NestedEnum> setOfEnum;
    private transient final Set<NestedIntEnum> setOfIntEnum;
    private transient final Set<NestedStruct> setOfStruct;
    private transient final Set<List<String>> setOfStringList;
    private transient final Set<Map<String, String>> setOfStringMap;

    private SetsAllTypesInput(Builder builder) {
        this.setOfBoolean = builder.setOfBoolean == null ? null : Collections.unmodifiableSet(builder.setOfBoolean);
        this.setOfNumber = builder.setOfNumber == null ? null : Collections.unmodifiableSet(builder.setOfNumber);
        this.setOfString = builder.setOfString == null ? null : Collections.unmodifiableSet(builder.setOfString);
        this.setOfBlobs = builder.setOfBlobs == null ? null : Collections.unmodifiableSet(builder.setOfBlobs);
        this.setOfTimestamps = builder.setOfTimestamps == null ? null : Collections.unmodifiableSet(builder.setOfTimestamps);
        this.setOfUnion = builder.setOfUnion == null ? null : Collections.unmodifiableSet(builder.setOfUnion);
        this.setOfEnum = builder.setOfEnum == null ? null : Collections.unmodifiableSet(builder.setOfEnum);
        this.setOfIntEnum = builder.setOfIntEnum == null ? null : Collections.unmodifiableSet(builder.setOfIntEnum);
        this.setOfStruct = builder.setOfStruct == null ? null : Collections.unmodifiableSet(builder.setOfStruct);
        this.setOfStringList = builder.setOfStringList == null ? null : Collections.unmodifiableSet(builder.setOfStringList);
        this.setOfStringMap = builder.setOfStringMap == null ? null : Collections.unmodifiableSet(builder.setOfStringMap);
    }

    public Set<Boolean> setOfBoolean() {
        if (setOfBoolean == null) {
            return Collections.emptySet();
        }
        return setOfBoolean;
    }

    public boolean hasSetOfBoolean() {
        return setOfBoolean != null;
    }

    public Set<Integer> setOfNumber() {
        if (setOfNumber == null) {
            return Collections.emptySet();
        }
        return setOfNumber;
    }

    public boolean hasSetOfNumber() {
        return setOfNumber != null;
    }

    public Set<String> setOfString() {
        if (setOfString == null) {
            return Collections.emptySet();
        }
        return setOfString;
    }

    public boolean hasSetOfString() {
        return setOfString != null;
    }

    public Set<ByteBuffer> setOfBlobs() {
        if (setOfBlobs == null) {
            return Collections.emptySet();
        }
        return setOfBlobs;
    }

    public boolean hasSetOfBlobs() {
        return setOfBlobs != null;
    }

    public Set<Instant> setOfTimestamps() {
        if (setOfTimestamps == null) {
            return Collections.emptySet();
        }
        return setOfTimestamps;
    }

    public boolean hasSetOfTimestamps() {
        return setOfTimestamps != null;
    }

    public Set<NestedUnion> setOfUnion() {
        if (setOfUnion == null) {
            return Collections.emptySet();
        }
        return setOfUnion;
    }

    public boolean hasSetOfUnion() {
        return setOfUnion != null;
    }

    public Set<NestedEnum> setOfEnum() {
        if (setOfEnum == null) {
            return Collections.emptySet();
        }
        return setOfEnum;
    }

    public boolean hasSetOfEnum() {
        return setOfEnum != null;
    }

    public Set<NestedIntEnum> setOfIntEnum() {
        if (setOfIntEnum == null) {
            return Collections.emptySet();
        }
        return setOfIntEnum;
    }

    public boolean hasSetOfIntEnum() {
        return setOfIntEnum != null;
    }

    public Set<NestedStruct> setOfStruct() {
        if (setOfStruct == null) {
            return Collections.emptySet();
        }
        return setOfStruct;
    }

    public boolean hasSetOfStruct() {
        return setOfStruct != null;
    }

    public Set<List<String>> setOfStringList() {
        if (setOfStringList == null) {
            return Collections.emptySet();
        }
        return setOfStringList;
    }

    public boolean hasSetOfStringList() {
        return setOfStringList != null;
    }

    public Set<Map<String, String>> setOfStringMap() {
        if (setOfStringMap == null) {
            return Collections.emptySet();
        }
        return setOfStringMap;
    }

    public boolean hasSetOfStringMap() {
        return setOfStringMap != null;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        SetsAllTypesInput that = (SetsAllTypesInput) other;
        return Objects.equals(this.setOfBoolean, that.setOfBoolean)
               && Objects.equals(this.setOfNumber, that.setOfNumber)
               && Objects.equals(this.setOfString, that.setOfString)
               && Objects.equals(this.setOfBlobs, that.setOfBlobs)
               && Objects.equals(this.setOfTimestamps, that.setOfTimestamps)
               && Objects.equals(this.setOfUnion, that.setOfUnion)
               && Objects.equals(this.setOfEnum, that.setOfEnum)
               && Objects.equals(this.setOfIntEnum, that.setOfIntEnum)
               && Objects.equals(this.setOfStruct, that.setOfStruct)
               && Objects.equals(this.setOfStringList, that.setOfStringList)
               && Objects.equals(this.setOfStringMap, that.setOfStringMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(setOfBoolean, setOfNumber, setOfString, setOfBlobs, setOfTimestamps, setOfUnion, setOfEnum, setOfIntEnum, setOfStruct, setOfStringList, setOfStringMap);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (setOfBoolean != null) {
            serializer.writeList(SCHEMA_SET_OF_BOOLEAN, setOfBoolean, SharedSerde.SetOfBooleansSerializer.INSTANCE);
        }
        if (setOfNumber != null) {
            serializer.writeList(SCHEMA_SET_OF_NUMBER, setOfNumber, SharedSerde.SetOfNumberSerializer.INSTANCE);
        }
        if (setOfString != null) {
            serializer.writeList(SCHEMA_SET_OF_STRING, setOfString, SharedSerde.SetOfStringsSerializer.INSTANCE);
        }
        if (setOfBlobs != null) {
            serializer.writeList(SCHEMA_SET_OF_BLOBS, setOfBlobs, SharedSerde.SetOfBlobsSerializer.INSTANCE);
        }
        if (setOfTimestamps != null) {
            serializer.writeList(SCHEMA_SET_OF_TIMESTAMPS, setOfTimestamps, SharedSerde.SetOfTimestampsSerializer.INSTANCE);
        }
        if (setOfUnion != null) {
            serializer.writeList(SCHEMA_SET_OF_UNION, setOfUnion, SharedSerde.SetOfUnionsSerializer.INSTANCE);
        }
        if (setOfEnum != null) {
            serializer.writeList(SCHEMA_SET_OF_ENUM, setOfEnum, SharedSerde.SetOfEnumsSerializer.INSTANCE);
        }
        if (setOfIntEnum != null) {
            serializer.writeList(SCHEMA_SET_OF_INT_ENUM, setOfIntEnum, SharedSerde.SetOfIntEnumsSerializer.INSTANCE);
        }
        if (setOfStruct != null) {
            serializer.writeList(SCHEMA_SET_OF_STRUCT, setOfStruct, SharedSerde.SetOfStructsSerializer.INSTANCE);
        }
        if (setOfStringList != null) {
            serializer.writeList(SCHEMA_SET_OF_STRING_LIST, setOfStringList, SharedSerde.SetOfStringListSerializer.INSTANCE);
        }
        if (setOfStringMap != null) {
            serializer.writeList(SCHEMA_SET_OF_STRING_MAP, setOfStringMap, SharedSerde.SetOfStringMapSerializer.INSTANCE);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SetsAllTypesInput}.
     */
    public static final class Builder implements ShapeBuilder<SetsAllTypesInput> {
        private Set<Boolean> setOfBoolean;
        private Set<Integer> setOfNumber;
        private Set<String> setOfString;
        private Set<ByteBuffer> setOfBlobs;
        private Set<Instant> setOfTimestamps;
        private Set<NestedUnion> setOfUnion;
        private Set<NestedEnum> setOfEnum;
        private Set<NestedIntEnum> setOfIntEnum;
        private Set<NestedStruct> setOfStruct;
        private Set<List<String>> setOfStringList;
        private Set<Map<String, String>> setOfStringMap;

        private Builder() {}

        public Builder setOfBoolean(Set<Boolean> setOfBoolean) {
            this.setOfBoolean = setOfBoolean;
            return this;
        }

        public Builder setOfNumber(Set<Integer> setOfNumber) {
            this.setOfNumber = setOfNumber;
            return this;
        }

        public Builder setOfString(Set<String> setOfString) {
            this.setOfString = setOfString;
            return this;
        }

        public Builder setOfBlobs(Set<ByteBuffer> setOfBlobs) {
            this.setOfBlobs = setOfBlobs;
            return this;
        }

        public Builder setOfTimestamps(Set<Instant> setOfTimestamps) {
            this.setOfTimestamps = setOfTimestamps;
            return this;
        }

        public Builder setOfUnion(Set<NestedUnion> setOfUnion) {
            this.setOfUnion = setOfUnion;
            return this;
        }

        public Builder setOfEnum(Set<NestedEnum> setOfEnum) {
            this.setOfEnum = setOfEnum;
            return this;
        }

        public Builder setOfIntEnum(Set<NestedIntEnum> setOfIntEnum) {
            this.setOfIntEnum = setOfIntEnum;
            return this;
        }

        public Builder setOfStruct(Set<NestedStruct> setOfStruct) {
            this.setOfStruct = setOfStruct;
            return this;
        }

        public Builder setOfStringList(Set<List<String>> setOfStringList) {
            this.setOfStringList = setOfStringList;
            return this;
        }

        public Builder setOfStringMap(Set<Map<String, String>> setOfStringMap) {
            this.setOfStringMap = setOfStringMap;
            return this;
        }

        @Override
        public SetsAllTypesInput build() {
            return new SetsAllTypesInput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.setOfBoolean(SharedSerde.deserializeSetOfBooleans(member, de));
                    case 1 -> builder.setOfNumber(SharedSerde.deserializeSetOfNumber(member, de));
                    case 2 -> builder.setOfString(SharedSerde.deserializeSetOfStrings(member, de));
                    case 3 -> builder.setOfBlobs(SharedSerde.deserializeSetOfBlobs(member, de));
                    case 4 -> builder.setOfTimestamps(SharedSerde.deserializeSetOfTimestamps(member, de));
                    case 5 -> builder.setOfUnion(SharedSerde.deserializeSetOfUnions(member, de));
                    case 6 -> builder.setOfEnum(SharedSerde.deserializeSetOfEnums(member, de));
                    case 7 -> builder.setOfIntEnum(SharedSerde.deserializeSetOfIntEnums(member, de));
                    case 8 -> builder.setOfStruct(SharedSerde.deserializeSetOfStructs(member, de));
                    case 9 -> builder.setOfStringList(SharedSerde.deserializeSetOfStringList(member, de));
                    case 10 -> builder.setOfStringMap(SharedSerde.deserializeSetOfStringMap(member, de));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.setOfBoolean(this.setOfBoolean);
        builder.setOfNumber(this.setOfNumber);
        builder.setOfString(this.setOfString);
        builder.setOfBlobs(this.setOfBlobs);
        builder.setOfTimestamps(this.setOfTimestamps);
        builder.setOfUnion(this.setOfUnion);
        builder.setOfEnum(this.setOfEnum);
        builder.setOfIntEnum(this.setOfIntEnum);
        builder.setOfStruct(this.setOfStruct);
        builder.setOfStringList(this.setOfStringList);
        builder.setOfStringMap(this.setOfStringMap);
        return builder;
    }

}

