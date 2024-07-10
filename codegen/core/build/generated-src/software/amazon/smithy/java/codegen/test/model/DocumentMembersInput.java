

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class DocumentMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#DocumentMembersInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("requiredDoc", PreludeSchemas.DOCUMENT,
            new RequiredTrait())
        .putMember("optionalDocument", PreludeSchemas.DOCUMENT)
        .build();

    private static final Schema SCHEMA_REQUIRED_DOC = SCHEMA.member("requiredDoc");
    private static final Schema SCHEMA_OPTIONAL_DOCUMENT = SCHEMA.member("optionalDocument");

    private transient final Document requiredDoc;
    private transient final Document optionalDocument;

    private DocumentMembersInput(Builder builder) {
        this.requiredDoc = builder.requiredDoc;
        this.optionalDocument = builder.optionalDocument;
    }

    public Document requiredDoc() {
        return requiredDoc;
    }

    public Document optionalDocument() {
        return optionalDocument;
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
        DocumentMembersInput that = (DocumentMembersInput) other;
        return Objects.equals(this.requiredDoc, that.requiredDoc)
               && Objects.equals(this.optionalDocument, that.optionalDocument);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredDoc, optionalDocument);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeDocument(SCHEMA_REQUIRED_DOC, requiredDoc);
        if (optionalDocument != null) {
            serializer.writeDocument(SCHEMA_OPTIONAL_DOCUMENT, optionalDocument);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DocumentMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<DocumentMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private Document requiredDoc;
        private Document optionalDocument;

        private Builder() {}

        public Builder requiredDoc(Document requiredDoc) {
            this.requiredDoc = Objects.requireNonNull(requiredDoc, "requiredDoc cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_DOC);
            return this;
        }

        public Builder optionalDocument(Document optionalDocument) {
            this.optionalDocument = optionalDocument;
            return this;
        }

        @Override
        public DocumentMembersInput build() {
            tracker.validate();
            return new DocumentMembersInput(this);
        }

        @Override
        public ShapeBuilder<DocumentMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_DOC)) {
                tracker.setMember(SCHEMA_REQUIRED_DOC);
            }
            return this;
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
                    case 0 -> builder.requiredDoc(de.readDocument());
                    case 1 -> builder.optionalDocument(de.readDocument());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredDoc(this.requiredDoc);
        builder.optionalDocument(this.optionalDocument);
        return builder;
    }

}

