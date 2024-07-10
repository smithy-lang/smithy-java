

package software.amazon.smithy.java.codegen.test.model;

import java.math.BigDecimal;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class TraitsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.traits#TraitsInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("stringDefault", PreludeSchemas.STRING,
            new DefaultTrait(Node.from("string")))
        .putMember("stringWithLength", PreludeSchemas.STRING,
            LengthTrait.builder().min(10L).build())
        .putMember("numberWithRange", PreludeSchemas.INTEGER,
            RangeTrait.builder().max(new BigDecimal("100")).build())
        .putMember("xmlNamespaced", PreludeSchemas.STRING,
            XmlNamespaceTrait.builder().uri("http://foo.com").build())
        .build();

    private static final Schema SCHEMA_STRING_DEFAULT = SCHEMA.member("stringDefault");
    private static final Schema SCHEMA_STRING_WITH_LENGTH = SCHEMA.member("stringWithLength");
    private static final Schema SCHEMA_NUMBER_WITH_RANGE = SCHEMA.member("numberWithRange");
    private static final Schema SCHEMA_XML_NAMESPACED = SCHEMA.member("xmlNamespaced");

    private transient final String stringDefault;
    private transient final String stringWithLength;
    private transient final Integer numberWithRange;
    private transient final String xmlNamespaced;

    private TraitsInput(Builder builder) {
        this.stringDefault = builder.stringDefault;
        this.stringWithLength = builder.stringWithLength;
        this.numberWithRange = builder.numberWithRange;
        this.xmlNamespaced = builder.xmlNamespaced;
    }

    public String stringDefault() {
        return stringDefault;
    }

    public String stringWithLength() {
        return stringWithLength;
    }

    public Integer numberWithRange() {
        return numberWithRange;
    }

    public String xmlNamespaced() {
        return xmlNamespaced;
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
        TraitsInput that = (TraitsInput) other;
        return Objects.equals(this.stringDefault, that.stringDefault)
               && Objects.equals(this.stringWithLength, that.stringWithLength)
               && Objects.equals(this.numberWithRange, that.numberWithRange)
               && Objects.equals(this.xmlNamespaced, that.xmlNamespaced);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stringDefault, stringWithLength, numberWithRange, xmlNamespaced);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_STRING_DEFAULT, stringDefault);
        if (stringWithLength != null) {
            serializer.writeString(SCHEMA_STRING_WITH_LENGTH, stringWithLength);
        }
        if (numberWithRange != null) {
            serializer.writeInteger(SCHEMA_NUMBER_WITH_RANGE, numberWithRange);
        }
        if (xmlNamespaced != null) {
            serializer.writeString(SCHEMA_XML_NAMESPACED, xmlNamespaced);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link TraitsInput}.
     */
    public static final class Builder implements ShapeBuilder<TraitsInput> {
        private static final String STRING_DEFAULT_DEFAULT = "string";
        private String stringDefault = STRING_DEFAULT_DEFAULT;
        private String stringWithLength;
        private Integer numberWithRange;
        private String xmlNamespaced;

        private Builder() {}

        public Builder stringDefault(String stringDefault) {
            this.stringDefault = Objects.requireNonNull(stringDefault, "stringDefault cannot be null");
            return this;
        }

        public Builder stringWithLength(String stringWithLength) {
            this.stringWithLength = stringWithLength;
            return this;
        }

        public Builder numberWithRange(int numberWithRange) {
            this.numberWithRange = numberWithRange;
            return this;
        }

        public Builder xmlNamespaced(String xmlNamespaced) {
            this.xmlNamespaced = xmlNamespaced;
            return this;
        }

        @Override
        public TraitsInput build() {
            return new TraitsInput(this);
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
                    case 0 -> builder.stringDefault(de.readString(member));
                    case 1 -> builder.stringWithLength(de.readString(member));
                    case 2 -> builder.numberWithRange(de.readInteger(member));
                    case 3 -> builder.xmlNamespaced(de.readString(member));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.stringDefault(this.stringDefault);
        builder.stringWithLength(this.stringWithLength);
        builder.numberWithRange(this.numberWithRange);
        builder.xmlNamespaced(this.xmlNamespaced);
        return builder;
    }

}

