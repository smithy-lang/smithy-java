/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.ValueType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.java.runtime.core.serde.any.Any;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class JsonAny implements Any {

    private final com.jsoniter.any.Any any;
    private final SdkSchema schema;
    private final TimestampFormatter defaultTimestampFormat;
    private final boolean useJsonName;
    private final boolean useTimestampFormat;

    JsonAny(SdkSchema schema, com.jsoniter.any.Any any, boolean useJsonName, TimestampFormatter defaultTimestampFormat,
            boolean useTimestampFormat) {
        this.schema = schema;
        this.any = any;
        this.useJsonName = useJsonName;
        this.defaultTimestampFormat = defaultTimestampFormat;
        this.useTimestampFormat = useTimestampFormat;
    }

    @Override
    public SdkSchema getSchema() {
        return schema;
    }

    @Override
    public ShapeType getType() {
        if (schema.type() != ShapeType.DOCUMENT) {
            return schema.type();
        } else {
            return switch (any.valueType()) {
                case NUMBER -> ShapeType.DOUBLE;
                case ARRAY -> ShapeType.LIST;
                case OBJECT -> ShapeType.MAP;
                case STRING -> ShapeType.STRING;
                case BOOLEAN -> ShapeType.BOOLEAN;
                default -> ShapeType.DOCUMENT;
            };
        }
    }

    @Override
    public boolean asBoolean() {
        return any.valueType() == ValueType.BOOLEAN ? any.toBoolean() : Any.super.asBoolean();
    }

    @Override
    public byte asByte() {
        return Any.super.asByte();
    }

    @Override
    public short asShort() {
        return any.valueType() == ValueType.NUMBER ? (short) any.toInt() : Any.super.asShort();
    }

    @Override
    public int asInteger() {
        return any.valueType() == ValueType.NUMBER ? any.toInt() : Any.super.asInteger();
    }

    @Override
    public long asLong() {
        return any.valueType() == ValueType.NUMBER ? any.toLong() : Any.super.asLong();
    }

    @Override
    public float asFloat() {
        return any.valueType() == ValueType.NUMBER ? any.toFloat() : Any.super.asFloat();
    }

    @Override
    public double asDouble() {
        return any.valueType() == ValueType.NUMBER ? any.toDouble() : Any.super.asDouble();
    }

    @Override
    public BigInteger asBigInteger() {
        return any.valueType() == ValueType.NUMBER ? BigInteger.valueOf(any.toLong()) : Any.super.asBigInteger();
    }

    @Override
    public BigDecimal asBigDecimal() {
        return any.valueType() == ValueType.NUMBER ? BigDecimal.valueOf(any.toLong()) : Any.super.asBigDecimal();
    }

    @Override
    public String asString() {
        return any.valueType() == ValueType.STRING ? any.toString() : Any.super.asString();
    }

    @Override
    public byte[] asBlob() {
        return any.valueType() == ValueType.STRING ? Base64.getDecoder().decode(any.toString()) : Any.super.asBlob();
    }

    @Override
    public Instant asTimestamp() {
        TimestampFormatter format = defaultTimestampFormat;

        if (useTimestampFormat) {
            format = getSchema().getTrait(TimestampFormatTrait.class).map(TimestampFormatter::of).orElse(format);
        }

        return switch (any.valueType()) {
            case NUMBER -> format.createFromNumber(any.toDouble());
            case STRING -> format.parseFromString(any.toString(), true);
            default -> {
                throw new IllegalStateException(
                        "Expected a string or number value for a timestamp, but found " + any.valueType());
            }
        };
    }

    @Override
    public List<Any> asList() {
        if (any.valueType() != ValueType.ARRAY) {
            return Any.super.asList();
        }

        List<Any> result = new ArrayList<>();
        SdkSchema member = schema.member("member", Any.SCHEMA);
        for (com.jsoniter.any.Any value : any) {
            result.add(new JsonAny(member, value, useJsonName, defaultTimestampFormat, useTimestampFormat));
        }

        return result;
    }

    @Override
    public Map<Any, Any> asMap() {
        if (any.valueType() != ValueType.OBJECT) {
            return Any.super.asMap();
        } else {
            Map<Any, Any> result = new LinkedHashMap<>();
            SdkSchema keyMember = schema.member("key", Any.SCHEMA);
            SdkSchema valueMember = schema.member("value", Any.SCHEMA);
            for (var entry : any.asMap().entrySet()) {
                result.put(
                        new JsonAny(keyMember, entry.getValue(), useJsonName, defaultTimestampFormat,
                                useTimestampFormat),
                        new JsonAny(valueMember, entry.getValue(), useJsonName, defaultTimestampFormat,
                                useTimestampFormat));
            }
            return result;
        }
    }

    @Override
    public Any getStructMember(String memberName) {
        SdkSchema member = schema.member(memberName, Any.SCHEMA);
        if (any.valueType() == ValueType.OBJECT) {
            String jsonName = !useJsonName
                    ? memberName
                    : member.getTrait(JsonNameTrait.class).map(JsonNameTrait::getValue).orElse(memberName);
            var result = any.get(jsonName);
            if (result != null) {
                return new JsonAny(member, result, useJsonName, defaultTimestampFormat, useTimestampFormat);
            }
        }
        return null;
    }
}
