/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.myservice.model;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.runtime.net.StoppableInputStream;
import software.amazon.smithy.java.runtime.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.serde.ToStringSerializer;
import software.amazon.smithy.java.runtime.shapes.IOShape;
import software.amazon.smithy.java.runtime.shapes.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

// Example of a potentially generated shape.
public final class PutPersonImageInput implements IOShape {

    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonInput");
    private static final SdkSchema SCHEMA_NAME = SdkSchema
            .memberBuilder(0, "name", SharedSchemas.STRING)
            .id(ID).traits(new HttpLabelTrait(), new RequiredTrait()).build();
    private static final SdkSchema SCHEMA_TAGS = SdkSchema
            .memberBuilder(1, "tags", SharedSchemas.STRING)
            .id(ID).traits(new HttpHeaderTrait("Tags")).build();
    private static final SdkSchema SCHEMA_MORE_TAGS = SdkSchema
            .memberBuilder(2, "moreTags", SharedSchemas.STRING)
            .id(ID).traits(new HttpQueryTrait("MoreTags")).build();
    private static final SdkSchema SCHEMA_IMAGE = SdkSchema
            .memberBuilder(3, "image", SharedSchemas.STREAM)
            .id(ID).traits(new HttpPayloadTrait()).build();
    static final SdkSchema SCHEMA = SdkSchema.builder()
            .id(ID)
            .type(ShapeType.STRUCTURE)
            .members(SCHEMA_NAME, SCHEMA_TAGS, SCHEMA_MORE_TAGS, SCHEMA_IMAGE)
            .build();

    private final String name;
    private final StoppableInputStream image;
    private final List<String> tags;
    private final List<String> moreTags;

    private PutPersonImageInput(Builder builder) {
        this.name = builder.name;
        this.image = builder.image;
        this.tags = builder.tags;
        this.moreTags = builder.moreTags;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() {
        return name;
    }

    public StoppableInputStream image() {
        return image;
    }

    public List<String> tags() {
        return tags;
    }

    public List<String> moreTags() {
        return moreTags;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serializeStream(Serializer encoder) {
        encoder.writeStreamingBlob(SCHEMA_IMAGE, image);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.beginStruct(SCHEMA, st -> {
            st.stringMember(SCHEMA_NAME, name);
            st.listMember(SCHEMA_TAGS, ser -> {
                tags.forEach(tag -> ser.writeString(SCHEMA_TAGS, tag));
            });
            st.listMember(SCHEMA_MORE_TAGS, ser -> {
                moreTags.forEach(tag -> ser.writeString(SCHEMA_MORE_TAGS, tag));
            });
        });
    }

    public static final class Builder implements IOShape.Builder<PutPersonImageInput> {

        private String name;
        private List<String> tags = new ArrayList<>();
        private List<String> moreTags = new ArrayList<>();
        private StoppableInputStream image = StoppableInputStream.ofEmpty();

        private Builder() {}

        @Override
        public PutPersonImageInput build() {
            return new PutPersonImageInput(this);
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder image(StoppableInputStream image) {
            this.image = image;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder moreTags(List<String> moreTags) {
            this.moreTags = moreTags;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, (member, de) -> {
                switch (member.memberIndex()) {
                    case 0 -> name(de.readString(member));
                    case 1 -> de.readList(SCHEMA_TAGS, ser -> tags.add(ser.readString(SCHEMA_TAGS)));
                    case 2 -> de.readList(SCHEMA_MORE_TAGS, ser -> moreTags.add(ser.readString(SCHEMA_MORE_TAGS)));
                }
            });
            return this;
        }

        @Override
        public IOShape.Builder<PutPersonImageInput> deserializeStream(Deserializer decoder) {
            image = decoder.readStream(SCHEMA_IMAGE);
            return this;
        }
    }
}
