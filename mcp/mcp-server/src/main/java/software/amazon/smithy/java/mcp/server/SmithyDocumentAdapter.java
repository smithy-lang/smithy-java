/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.DATE_TIME;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.EPOCH_SECONDS;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.HTTP_DATE;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.mcp.OneOfTrait;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Adapts schemaless MCP documents to and from Smithy runtime values.
 */
final class SmithyDocumentAdapter {
    private static final TraitKey<OneOfTrait> ONE_OF_TRAIT = TraitKey.get(OneOfTrait.class);

    private final SchemaIndex schemaIndex;
    private final Map<Schema, Boolean> adaptationRequired =
            Collections.synchronizedMap(new IdentityHashMap<>());

    SmithyDocumentAdapter(SchemaIndex schemaIndex) {
        this.schemaIndex = schemaIndex;
    }

    Document toSmithy(Document document, Schema schema) {
        if (document == null) {
            return null;
        }
        if (!needsAdaptation(schema)) {
            return document;
        }
        var fromType = document.type();
        var toType = schema.type();
        return switch (toType) {
            case BIG_DECIMAL -> switch (fromType) {
                case STRING -> Document.of(new BigDecimal(document.asString()));
                case INTEGER, LONG, BIG_INTEGER, FLOAT, DOUBLE, BIG_DECIMAL ->
                    Document.of(new BigDecimal(document.asNumber().toString()));
                default -> badType(fromType, toType);
            };
            case BIG_INTEGER -> switch (fromType) {
                case STRING -> Document.of(new BigInteger(document.asString()));
                case INTEGER, LONG, BIG_INTEGER -> Document.of(document.asBigInteger());
                default -> badType(fromType, toType);
            };
            case BLOB -> switch (fromType) {
                case STRING -> Document.of(Base64.getDecoder().decode(document.asString()));
                case BLOB -> document;
                default -> badType(fromType, toType);
            };
            case TIMESTAMP -> adaptTimestamp(document, schema, false);
            case STRUCTURE -> {
                var converted = new HashMap<String, Document>();
                for (var member : schema.members()) {
                    var memberDocument = document.getMember(member.memberName());
                    if (memberDocument != null) {
                        converted.put(member.memberName(), toSmithy(memberDocument, member));
                    }
                }
                yield Document.of(converted);
            }
            case UNION -> {
                var converted = new HashMap<String, Document>();
                for (var member : schema.members()) {
                    var memberDocument = document.getMember(member.memberName());
                    if (memberDocument != null) {
                        converted.put(member.memberName(), toSmithy(memberDocument, member));
                        break;
                    }
                }
                yield Document.of(converted);
            }
            case LIST, SET -> {
                var converted = new ArrayList<Document>();
                for (var item : document.asList()) {
                    converted.add(toSmithy(item, schema.listMember()));
                }
                yield Document.of(converted);
            }
            case MAP -> {
                var converted = new HashMap<String, Document>();
                for (var entry : document.asStringMap().entrySet()) {
                    converted.put(entry.getKey(), toSmithy(entry.getValue(), schema.mapValueMember()));
                }
                yield Document.of(converted);
            }
            case DOCUMENT -> toSmithyOneOf(document, schema);
            default -> document;
        };
    }

    Document fromSmithy(Document document, Schema schema) {
        if (document == null) {
            return null;
        }
        if (!needsAdaptation(schema)) {
            return document;
        }
        return switch (schema.type()) {
            case BIG_DECIMAL -> Document.of(document.asBigDecimal().toString());
            case BIG_INTEGER -> Document.of(document.asBigInteger().toString());
            case BLOB -> Document.of(Base64.getEncoder().encodeToString(ByteBufferUtils.getBytes(document.asBlob())));
            case TIMESTAMP -> adaptTimestamp(document, schema, true);
            case STRUCTURE -> {
                var converted = new HashMap<String, Document>();
                for (var member : schema.members()) {
                    var memberDocument = document.getMember(member.memberName());
                    if (memberDocument != null) {
                        converted.put(member.memberName(), fromSmithy(memberDocument, member));
                    }
                }
                yield Document.of(converted);
            }
            case UNION -> {
                Document converted = Document.of(Map.of());
                for (var member : schema.members()) {
                    var memberDocument = document.getMember(member.memberName());
                    if (memberDocument != null) {
                        converted = Document.of(Map.of(
                                member.memberName(),
                                fromSmithy(memberDocument, member)));
                        break;
                    }
                }
                yield converted;
            }
            case LIST, SET -> {
                var converted = new ArrayList<Document>();
                for (var item : document.asList()) {
                    converted.add(fromSmithy(item, schema.listMember()));
                }
                yield Document.of(converted);
            }
            case MAP -> {
                var converted = new HashMap<String, Document>();
                for (var entry : document.asStringMap().entrySet()) {
                    converted.put(entry.getKey(), fromSmithy(entry.getValue(), schema.mapValueMember()));
                }
                yield Document.of(converted);
            }
            case DOCUMENT -> fromSmithyOneOf(document, schema);
            default -> document;
        };
    }

    private Document toSmithyOneOf(Document document, Schema schema) {
        var targetSchema = schema.isMember() ? schema.memberTarget() : schema;
        var oneOf = targetSchema.getTrait(ONE_OF_TRAIT);
        if (oneOf == null) {
            return document;
        }

        for (var member : oneOf.getMembers()) {
            var memberDocument = document.getMember(member.getName());
            if (memberDocument != null) {
                var converted = new HashMap<String, Document>();
                converted.put(oneOf.getDiscriminator(), Document.of(member.getTarget().toString()));
                converted.putAll(toSmithy(memberDocument, schemaIndex.getSchema(member.getTarget())).asStringMap());
                return Document.of(converted);
            }
        }
        return document;
    }

    private Document fromSmithyOneOf(Document document, Schema schema) {
        var targetSchema = schema.isMember() ? schema.memberTarget() : schema;
        var oneOf = targetSchema.getTrait(ONE_OF_TRAIT);
        if (oneOf == null) {
            return document;
        }

        var shapeId = resolveVariant(document, oneOf);
        var defaulted = shapeId == null;
        if (defaulted) {
            shapeId = oneOf.getDefaultTarget().orElse(null);
            if (shapeId == null) {
                return document;
            }
            // Bundle loading may skip model validation.
            var target = shapeId;
            if (oneOf.getMembers().stream().filter(member -> member.getTarget().equals(target)).count() != 1) {
                throw new SerializationException("The oneOf defaultTarget `" + shapeId
                        + "` must identify exactly one member of " + targetSchema.id());
            }
        }
        for (var member : oneOf.getMembers()) {
            if (member.getTarget().equals(shapeId)) {
                var variantSchema = schemaIndex.getSchema(shapeId);
                if (defaulted && variantSchema.type() != ShapeType.STRUCTURE) {
                    throw new SerializationException("The oneOf defaultTarget `" + shapeId
                            + "` must target a structure");
                }
                var converted = new HashMap<>(
                        fromSmithy(document, variantSchema).asStringMap());
                converted.remove(oneOf.getDiscriminator());
                return Document.of(Map.of(member.getName(), Document.of(converted)));
            }
        }
        return document;
    }

    /**
     * Resolves the variant named by the discriminator member, or by the shape ID of a typed document such as
     * {@code Document.of(shape)}, which carries no discriminator member. Returns null for untagged documents.
     */
    private ShapeId resolveVariant(Document document, OneOfTrait oneOf) {
        var discriminator = document.getMember(oneOf.getDiscriminator());
        if (discriminator != null) {
            return ShapeId.from(discriminator.asString());
        }
        // Only typed structures report their own shape ID; map documents stay untagged.
        if (document.type() != ShapeType.STRUCTURE) {
            return null;
        }
        var shapeId = document.discriminator();
        // A document backed by a member schema reports the member ID; resolve it to the member's target.
        if (shapeId != null && shapeId.hasMember()) {
            var container = findSchema(shapeId.withoutMember());
            var member = container == null ? null : container.member(shapeId.getMember().get());
            shapeId = member == null ? null : member.memberTarget().id();
        }
        return shapeId;
    }

    private Schema findSchema(ShapeId id) {
        // Some indexes throw for unknown shapes instead of returning null (e.g. composed or model-backed indexes);
        // an unresolved lookup only means the variant is resolved through the defaultTarget instead.
        try {
            return schemaIndex.getSchema(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean needsAdaptation(Schema schema) {
        return adaptationRequired.computeIfAbsent(
                schema,
                ignored -> needsAdaptation(
                        schema,
                        Collections.newSetFromMap(new IdentityHashMap<>())));
    }

    private boolean needsAdaptation(Schema schema, Set<Schema> visiting) {
        var target = schema.isMember() ? schema.memberTarget() : schema;
        if (!visiting.add(schema)) {
            return false;
        }
        try {
            return switch (target.type()) {
                case BIG_DECIMAL, BIG_INTEGER, BLOB, TIMESTAMP, DOCUMENT, UNION -> true;
                case STRUCTURE -> target.members()
                        .stream()
                        .anyMatch(member -> needsAdaptation(member, visiting));
                case LIST, SET -> needsAdaptation(target.listMember(), visiting);
                case MAP -> needsAdaptation(target.mapValueMember(), visiting);
                default -> false;
            };
        } finally {
            visiting.remove(schema);
        }
    }

    private static Document badType(ShapeType from, ShapeType to) {
        throw new IllegalArgumentException("Cannot convert from " + from + " to " + to);
    }

    private static Document adaptTimestamp(Document document, Schema schema, boolean output) {
        if (document.isType(ShapeType.TIMESTAMP)) {
            return Document.of(DATE_TIME.writeString(document.asTimestamp()));
        }
        var timestamp = readTimestamp(document, schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT));
        return output ? Document.of(DATE_TIME.writeString(timestamp)) : Document.of(timestamp);
    }

    private static Instant readTimestamp(Document document, TimestampFormatTrait trait) {
        if (trait != null) {
            try {
                var formatter = TimestampFormatter.of(trait);
                if (!document.isType(ShapeType.STRING)) {
                    return formatter.readFromNumber(document.asNumber());
                }
                // Epoch-seconds timestamps are numbers; strings only use the date-time and http-date parsing below.
                if (formatter.format() != TimestampFormatTrait.Format.EPOCH_SECONDS) {
                    return formatter.readFromString(document.asString(), false);
                }
            } catch (RuntimeException e) {
                // Fall back to legacy parsing for unknown formats and format mismatches.
            }
        }
        if (document.isType(ShapeType.STRING)) {
            var value = document.asString();
            try {
                return DATE_TIME.readFromString(value, false);
            } catch (RuntimeException e) {
                return HTTP_DATE.readFromString(value, false);
            }
        }
        return EPOCH_SECONDS.readFromNumber(document.asNumber());
    }
}
