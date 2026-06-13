/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Wraps another {@link Document} to give it a {@code document}-typed model {@link Schema}, delegating all value
 * accessors to the wrapped document.
 *
 * <p>Used only for members whose modeled shape is an (untyped) {@code document}: the inner value can be any document
 * kind, so unlike {@link ContentDocument} (which holds an already-unwrapped scalar/collection value for a known
 * scalar/aggregate schema) this type must forward every accessor. Document-typed members are uncommon and never on
 * the scalar/aggregate hot path, so the extra delegation here is not performance sensitive.
 */
record SchemaDocument(Schema schema, Document document) implements Document {
    @Override
    public ShapeType type() {
        return schema.type();
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        if (schema.type() == ShapeType.DOCUMENT) {
            serializer.writeDocument(schema, this);
        } else {
            serializeContents(serializer);
        }
    }

    @Override
    public void serializeContents(ShapeSerializer serializer) {
        document.serializeContents(serializer);
    }

    @Override
    public BigDecimal asBigDecimal() {
        return document.asBigDecimal();
    }

    @Override
    public BigInteger asBigInteger() {
        return document.asBigInteger();
    }

    @Override
    public ByteBuffer asBlob() {
        return document.asBlob();
    }

    @Override
    public boolean asBoolean() {
        return document.asBoolean();
    }

    @Override
    public byte asByte() {
        return document.asByte();
    }

    @Override
    public double asDouble() {
        return document.asDouble();
    }

    @Override
    public float asFloat() {
        return document.asFloat();
    }

    @Override
    public int asInteger() {
        return document.asInteger();
    }

    @Override
    public List<Document> asList() {
        return document.asList();
    }

    @Override
    public long asLong() {
        return document.asLong();
    }

    @Override
    public Number asNumber() {
        return document.asNumber();
    }

    @Override
    public Object asObject() {
        return document.asObject();
    }

    @Override
    public <T extends SerializableShape> T asShape(ShapeBuilder<T> builder) {
        return document.asShape(builder);
    }

    @Override
    public short asShort() {
        return document.asShort();
    }

    @Override
    public String asString() {
        return document.asString();
    }

    @Override
    public Map<String, Document> asStringMap() {
        return document.asStringMap();
    }

    @Override
    public Instant asTimestamp() {
        return document.asTimestamp();
    }

    @Override
    public int size() {
        return document.size();
    }

    @Override
    public Document getMember(String memberName) {
        return document.getMember(memberName);
    }

    @Override
    public Set<String> getMemberNames() {
        return document.getMemberNames();
    }
}
