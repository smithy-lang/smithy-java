/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.document.DocumentUtils;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A document implementation that also implements {@link SerializableStruct}.
 *
 * <p>A discriminator can be defined using "__type".
 *
 * <p>Note that this implementation does break the invariant of Document that {@link #serialize} always serializes
 * itself as a document, and then serializes the contents. That's because this implementation of Document is meant to
 * stand-in for a modeled value and not get serialized as a document.
 */
public final class WrappedDocument implements Document, SerializableStruct {

    private final Schema schema;
    private final ShapeId service;
    private final Document delegate;

    /**
     * @param schema Schema to use as the schema of the wrapped document.
     * @param delegate The document to wrap.
     * @param service The shape ID of a service, used to provide a default namespace to relative document shape IDs.
     */
    public WrappedDocument(Schema schema, Document delegate, ShapeId service) {
        this.service = service;
        this.schema = schema;
        this.delegate = delegate;
    }

    @Override
    public Schema schema() {
        return schema;
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        if (schema.type() == ShapeType.DOCUMENT) {
            // If the schema is literally a document, then do write the wrapping document before serializing contents.
            serializer.writeDocument(schema, this);
        } else {
            // Don't serialize as a document, but rather serialize the contents directly.
            serializeContents(serializer);
        }
    }

    @Override
    public void serializeContents(ShapeSerializer serializer) {
        switch (schema.type()) {
            case STRUCTURE, UNION -> serializer.writeStruct(schema, this);
            default -> delegate.serializeContents(new SchemaInterceptingSerializer(service, schema, serializer));
        }
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        for (var name : getMemberNames()) {
            var value = getMember(name);
            if (value != null) {
                var member = schema.member(name);
                if (member != null) {
                    value.serializeContents(new SchemaInterceptingSerializer(service, member, serializer));
                }
            }
        }
    }

    @Override
    public <T> T getMemberValue(Schema member) {
        return DocumentUtils.getMemberValue(this, schema, member);
    }

    @Override
    public ShapeType type() {
        return schema.type();
    }

    @Override
    public ShapeId discriminator() {
        var map = delegate.asStringMap();
        var type = map.get("__type");
        if (type != null) {
            return ShapeId.fromOptionalNamespace(service.getNamespace(), type.asString());
        } else {
            return delegate.discriminator();
        }
    }

    @Override
    public BigDecimal asBigDecimal() {
        return delegate.asBigDecimal();
    }

    @Override
    public BigInteger asBigInteger() {
        return delegate.asBigInteger();
    }

    @Override
    public ByteBuffer asBlob() {
        return delegate.asBlob();
    }

    @Override
    public boolean asBoolean() {
        return delegate.asBoolean();
    }

    @Override
    public byte asByte() {
        return delegate.asByte();
    }

    @Override
    public double asDouble() {
        return delegate.asDouble();
    }

    @Override
    public float asFloat() {
        return delegate.asFloat();
    }

    @Override
    public int asInteger() {
        return delegate.asInteger();
    }

    @Override
    public long asLong() {
        return delegate.asLong();
    }

    @Override
    public Number asNumber() {
        return delegate.asNumber();
    }

    @Override
    public short asShort() {
        return delegate.asShort();
    }

    @Override
    public String asString() {
        return delegate.asString();
    }

    @Override
    public Instant asTimestamp() {
        return delegate.asTimestamp();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public List<Document> asList() {
        var list = delegate.asList();
        List<Document> result = new ArrayList<>(list.size());
        var member = schema.listMember();
        for (var value : list) {
            result.add(new WrappedDocument(member, value, service));
        }
        return result;
    }

    @Override
    public Map<String, Document> asStringMap() {
        var map = delegate.asStringMap();
        Map<String, Document> wrapped = new LinkedHashMap<>(delegate.size());
        if (type() == ShapeType.MAP) {
            var member = schema.mapValueMember();
            for (var entry : map.entrySet()) {
                wrapped.put(entry.getKey(), new WrappedDocument(member, entry.getValue(), service));
            }
        } else {
            for (var entry : map.entrySet()) {
                var member = schema.member(entry.getKey());
                if (member != null) {
                    wrapped.put(entry.getKey(), new WrappedDocument(member, entry.getValue(), service));
                } else {
                    wrapped.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return wrapped;
    }

    @Override
    public Object asObject() {
        return delegate.asObject();
    }

    @Override
    public Document getMember(String memberName) {
        var member = schema.member(memberName);
        if (member == null) {
            return delegate.getMember(memberName);
        } else {
            var delegatedValue = delegate.getMember(memberName);
            return delegatedValue == null ? null : new WrappedDocument(member, delegate.getMember(memberName), service);
        }
    }

    @Override
    public Set<String> getMemberNames() {
        return delegate.getMemberNames();
    }
}
