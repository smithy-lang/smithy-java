/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.DATE_TIME;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.EPOCH_SECONDS;
import static software.amazon.smithy.java.core.serde.TimestampFormatter.Prelude.HTTP_DATE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import software.amazon.smithy.ai.McpHeaderTrait;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.BigNumbers;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.mcp.OneOfMember;
import software.amazon.smithy.java.mcp.OneOfTrait;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Adapts schemaless MCP documents to and from Smithy runtime values.
 */
final class SmithyDocumentAdapter {
    private static final TraitKey<OneOfTrait> ONE_OF_TRAIT = TraitKey.get(OneOfTrait.class);
    private static final TraitKey<McpHeaderTrait> MCP_HEADER_TRAIT = TraitKey.get(McpHeaderTrait.class);

    private final SchemaIndex schemaIndex;
    private final Map<Schema, Boolean> adaptationRequired =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Schema, Boolean> typeAdaptationRequired =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Schema, Set<String>> wireNames =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Schema, Boolean> resultAdaptationRequired =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final AtomicLong shapesSearched = new AtomicLong();
    private final Map<Schema, Omitted> overlapping =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Schema, Map<String, Omitted>> omittedFromVariant =
            Collections.synchronizedMap(new IdentityHashMap<>());

    SmithyDocumentAdapter(SchemaIndex schemaIndex) {
        this.schemaIndex = schemaIndex;
    }

    /**
     * How many shapes were looked at to find out what needs adaptation, for tests of how much is looked at.
     */
    long shapesSearched() {
        return shapesSearched.get();
    }

    Document toSmithy(Document document, Schema schema) {
        return toSmithy(document, schema, false, false);
    }

    /**
     * @param content true below a {@code @oneOf} document member, where the result stays an untyped document
     *                instead of being read into a shape, so properties that are not modeled are kept.
     * @param passedOn true when the document reaches the handler as it was sent and is only converted for what
     *                 the conversion refuses.
     */
    private Document toSmithy(Document document, Schema schema, boolean content, boolean passedOn) {
        if (document == null) {
            return null;
        }
        if (!needsAdaptation(schema)) {
            return document;
        }
        var toType = schema.type();
        if (toType == ShapeType.STRUCTURE || toType == ShapeType.UNION) {
            document = unwrap(document);
        }
        var fromType = document.type();
        return switch (toType) {
            case BIG_DECIMAL -> switch (fromType) {
                case STRING -> Document.of(BigNumbers.parseBigDecimal(document.asString()));
                case INTEGER, LONG, BIG_INTEGER, FLOAT, DOUBLE, BIG_DECIMAL ->
                    Document.of(new BigDecimal(document.asNumber().toString()));
                default -> badType(fromType, toType);
            };
            case BIG_INTEGER -> switch (fromType) {
                case STRING -> Document.of(BigNumbers.parseBigInteger(document.asString()));
                case INTEGER, LONG, BIG_INTEGER -> Document.of(document.asBigInteger());
                default -> badType(fromType, toType);
            };
            case BLOB -> switch (fromType) {
                case STRING -> Document.of(Base64.getDecoder().decode(document.asString()));
                case BLOB -> document;
                default -> badType(fromType, toType);
            };
            case TIMESTAMP -> adaptTimestamp(document, schema, false);
            case STRUCTURE -> structureToSmithy(document, schema, content, omitted(schema, null), passedOn);
            case UNION -> {
                var converted = new HashMap<String, Document>();
                var supplied = Supplied.of(document);
                var union = schema.isMember() ? schema.memberTarget() : schema;
                var omitted = omitted(schema, null);
                requireAvailableMember(schema);
                Schema selected = null;
                for (var member : schema.members()) {
                    if (omitted.members().contains(member.memberName())) {
                        requireNotSupplied(supplied, member, union, omitted);
                        continue;
                    }
                    // A member the caller supplied counts, whatever it holds.
                    var key = supplied.key(member, false);
                    if (key == null) {
                        continue;
                    }
                    if (selected != null) {
                        throw new IllegalArgumentException("Union " + union.id().getName()
                                + " accepts exactly one member, but found `" + McpJson.wireName(selected)
                                + "` and `" + McpJson.wireName(member) + "`");
                    }
                    selected = member;
                    var memberDocument = supplied.value(key);
                    if (memberDocument != null) {
                        converted.put(member.memberName(), toSmithy(memberDocument, member, content, passedOn));
                    } else {
                        // Selected, and holding null: still a value for a union that takes none, and a value
                        // of a plain document, which stays selected in content.
                        requireAvailableMember(member);
                        if (content && isPlainDocument(member)) {
                            converted.put(member.memberName(), null);
                        }
                    }
                    convertShadowed(supplied, member, content, passedOn);
                }
                yield Document.of(converted);
            }
            case LIST, SET -> {
                var converted = new ArrayList<Document>();
                for (var item : document.asList()) {
                    converted.add(toSmithy(item, schema.listMember(), content, passedOn));
                }
                yield Document.of(converted);
            }
            case MAP -> {
                var converted = new LinkedHashMap<String, Document>();
                for (var entry : document.asStringMap().entrySet()) {
                    converted.put(entry.getKey(),
                            toSmithy(entry.getValue(), schema.mapValueMember(), content, passedOn));
                }
                yield Document.of(converted);
            }
            case DOCUMENT -> toSmithyOneOf(document, schema, passedOn);
            default -> document;
        };
    }

    Document fromSmithy(Document document, Schema schema) {
        return fromSmithy(document, schema, false);
    }

    /**
     * @param untypedContent true below a document member, where content may be an untyped map and is flattened
     *                       into one, so member renames have to be applied here. Elsewhere outputs are typed
     *                       documents, which the codec already serializes with their wire names.
     */
    private Document fromSmithy(Document document, Schema schema, boolean untypedContent) {
        if (document == null) {
            return null;
        }
        if (!(untypedContent
                ? needsAdaptation(schema) || mayHoldForeignNames(document, schema)
                : needsResultAdaptation(schema))) {
            return document;
        }
        var toType = schema.type();
        var source = toType == ShapeType.STRUCTURE || toType == ShapeType.UNION ? unwrap(document) : document;
        return switch (toType) {
            case BIG_DECIMAL -> Document.of(source.asBigDecimal().toString());
            case BIG_INTEGER -> Document.of(source.asBigInteger().toString());
            case BLOB -> Document.of(Base64.getEncoder().encodeToString(ByteBufferUtils.getBytes(source.asBlob())));
            case TIMESTAMP -> adaptTimestamp(source, schema, true);
            case STRUCTURE -> structureFromSmithy(source, schema, untypedContent, omitted(schema, null));
            case UNION -> {
                Document converted = Document.of(Map.of());
                var omitted = omitted(schema, null).members();
                var unavailable = noMemberAvailable(schema);
                if (unavailable != null) {
                    throw new UnavailableResultException(unavailable);
                }
                var supplied = Supplied.of(source);
                // Before anything is selected, and wherever it is declared: an empty union, or another member,
                // would not tell that the result held a member that is not available.
                for (var member : schema.members()) {
                    if (omitted.contains(member.memberName()) && supplied.key(member, true) != null) {
                        var union = schema.isMember() ? schema.memberTarget() : schema;
                        throw new UnavailableResultException("Member `" + union.id().getName() + "$"
                                + member.memberName() + "` is not available over MCP");
                    }
                }
                for (var member : schema.members()) {
                    if (omitted.contains(member.memberName())) {
                        continue;
                    }
                    var key = supplied.key(member, true);
                    var memberDocument = key == null ? null : supplied.value(key);
                    if (memberDocument != null) {
                        converted = Document.of(Map.of(
                                McpJson.wireName(member),
                                fromSmithy(memberDocument, member, untypedContent)));
                        break;
                    }
                    if (key != null) {
                        // Selected, and holding null: as for arguments.
                        var none = noMemberAvailable(member);
                        if (none != null) {
                            throw new UnavailableResultException(none);
                        }
                        if (untypedContent && isPlainDocument(member)) {
                            var selected = new HashMap<String, Document>();
                            selected.put(McpJson.wireName(member), null);
                            converted = Document.of(selected);
                            break;
                        }
                    }
                }
                yield converted;
            }
            case LIST, SET -> {
                var converted = new ArrayList<Document>();
                for (var item : source.asList()) {
                    converted.add(fromSmithy(item, schema.listMember(), untypedContent));
                }
                yield Document.of(converted);
            }
            case MAP -> {
                var converted = new LinkedHashMap<String, Document>();
                // A typed object can stand in for a map. Its entries are then keyed as its own schema names
                // them on the wire, which is how the codec writes it.
                var own = isTyped(source) ? contents(source).schema : null;
                for (var entry : source.asStringMap().entrySet()) {
                    var member = own == null ? null : own.member(entry.getKey());
                    converted.put(
                            member == null ? entry.getKey() : McpJson.wireName(member),
                            fromSmithy(entry.getValue(), schema.mapValueMember(), untypedContent));
                }
                yield Document.of(converted);
            }
            case DOCUMENT -> fromSmithyOneOf(source, schema);
            default -> source;
        };
    }

    /**
     * Converts the members of a structure. This is also the check of what was sent: a key for a member that is
     * left out is refused here, and so is whatever the conversion of a member refuses. There is no other pass
     * over arguments, so that what is checked and what is bound cannot differ.
     */
    private Document structureToSmithy(
            Document document,
            Schema schema,
            boolean content,
            Omitted omitted,
            boolean passedOn
    ) {
        var converted = new LinkedHashMap<String, Document>();
        var supplied = Supplied.of(document);
        var target = schema.isMember() ? schema.memberTarget() : schema;
        var keepsNull = content && !needsTypeAdaptation(schema);
        for (var member : schema.members()) {
            if (omitted.members().contains(member.memberName())) {
                requireNotSupplied(supplied, member, target, omitted);
                continue;
            }
            var key = supplied.key(member, false);
            var memberDocument = key == null ? null : supplied.value(key);
            if (memberDocument != null) {
                converted.put(member.memberName(), toSmithy(memberDocument, member, content, passedOn));
            } else if (key != null) {
                // The member is there and holds null. A union without any available member takes no value,
                // and a key for it is one. Null is a value of a document member, and where content is passed
                // through a member that was given as null stays.
                requireAvailableMember(member);
                if (keepsNull || (content && isPlainDocument(member))) {
                    converted.put(member.memberName(), null);
                }
            }
            convertShadowed(supplied, member, content, passedOn);
        }
        if (content) {
            copyUnmodeledProperties(document, schema, converted, false, omitted);
        }
        return Document.of(converted);
    }

    /**
     * The schema of a union whose members are all left out accepts nothing: it can only be absent.
     *
     * @return the message for a union that has no member, or null.
     */
    private String noMemberAvailable(Schema schema) {
        var union = schema.isMember() ? schema.memberTarget() : schema;
        return union.type() == ShapeType.UNION
                && !union.members().isEmpty()
                && omitted(union, null).members().size() == union.members().size()
                        ? "Union " + union.id().getName() + " has no member that is available over MCP"
                        : null;
    }

    private void requireAvailableMember(Schema schema) {
        var message = noMemberAvailable(schema);
        if (message != null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Dropping a key for a member that is left out would hide from the caller that it had no effect, so it is
     * refused under any of its names and whatever it holds. The discriminator key is not such a name: it is
     * checked where the variant is.
     */
    private static void requireNotSupplied(Supplied supplied, Schema member, Schema container, Omitted omitted) {
        // A typed document knows its members by name only, and holds no discriminator.
        var keys = supplied.typed()
                ? List.of(member.memberName())
                : List.of(McpJson.wireName(member), member.memberName());
        for (var key : keys) {
            if (supplied.holds(key) && (supplied.typed() || !key.equals(omitted.discriminator()))) {
                throw new IllegalArgumentException("`" + key + "` names a member of " + container.id().getName()
                        + " that is not available over MCP");
            }
        }
    }

    /**
     * When a document holds a member under both of its names, one is bound and the other is not read. A
     * document that reaches a handler as it was sent holds both, and the handler may read the other one, so
     * that one is converted as well, for what the conversion refuses.
     */
    private void convertShadowed(Supplied supplied, Schema member, boolean content, boolean passedOn) {
        var shadowed = passedOn ? supplied.shadowed(member) : null;
        if (shadowed != null) {
            toSmithy(shadowed, member, content, true);
        }
    }

    private Document structureFromSmithy(Document source, Schema schema, boolean untypedContent, Omitted omitted) {
        var converted = new LinkedHashMap<String, Document>();
        var supplied = Supplied.of(source);
        // Where no type is adapted, a typed result used to be written by the codec and untyped content was
        // passed on as it was: a member that holds null was there as null.
        var keepsNull = !needsTypeAdaptation(schema);
        for (var member : schema.members()) {
            if (omitted.members().contains(member.memberName())) {
                continue;
            }
            var key = supplied.key(member, true);
            var memberDocument = key == null ? null : supplied.value(key);
            if (memberDocument != null) {
                converted.put(McpJson.wireName(member), fromSmithy(memberDocument, member, untypedContent));
            } else if (key != null && (keepsNull || (untypedContent && isPlainDocument(member)))) {
                converted.put(McpJson.wireName(member), null);
            }
        }
        if (untypedContent) {
            copyUnmodeledProperties(source, schema, converted, true, omitted);
        }
        return Document.of(converted);
    }

    /**
     * Below a document, a typed structure may be backed by another version of its schema, whose names the codec
     * would write, including a name that this version gives to another member. It can also sit anywhere below
     * a map that has no schema. So every object is converted member by member like other content, and so is
     * what a list or map may hold, in any form {@link Supplied} reads.
     */
    private static boolean mayHoldForeignNames(Document document, Schema schema) {
        return switch (schema.type()) {
            case STRUCTURE, MAP -> isObject(unwrap(document));
            case LIST, SET -> document.isType(ShapeType.LIST);
            default -> false;
        };
    }

    /**
     * What a document holds for the members of a structure or union, or for the variants of a {@code @oneOf}
     * document. Everything that reads members goes through this, in both directions, so that there is one
     * answer to which names count and whether a member is there.
     *
     * <p>Names follow one convention on each side of the adapter: wire names ({@code @jsonName}) on the MCP side,
     * and Smithy member names on the handler side, which is also how the content of a flattened {@code @oneOf}
     * document is keyed. Typed documents are always read by member name. Untyped documents are read by the name of
     * the side they come from: arguments by wire name, results by member name. The other name is accepted as an
     * alias when the first one is absent. A member is there when its key is, or when a typed document wrote
     * it, whatever it holds.
     *
     * <p>This is only sound because no key names two members: members for which that does not hold are left out
     * before they are read, see {@link #overlappingMembers}.
     *
     * <p>The content of a {@code @oneOf} document is opaque to the protocol of a backend, so a service that
     * forwards it sends the member names, whatever names that protocol uses elsewhere.
     *
     * <p>A member that is mirrored into an HTTP header never has an alias: intermediaries compare the header with
     * the argument they find under the advertised name.
     */
    private record Supplied(boolean typed, Map<String, Document> entries) {
        static Supplied of(Document document) {
            // A typed document gives its members by member name, including one it wrote as null.
            return new Supplied(isTyped(document), document.asStringMap());
        }

        boolean holds(String key) {
            return entries.containsKey(key);
        }

        Document value(String key) {
            return entries.get(key);
        }

        /**
         * The key under which the document holds a member, or null if it holds none.
         */
        String key(Schema member, boolean result) {
            var memberName = member.memberName();
            if (typed) {
                return entries.containsKey(memberName) ? memberName : null;
            }
            var wireName = McpJson.wireName(member);
            var name = result ? memberName : wireName;
            if (entries.containsKey(name)) {
                return name;
            }
            var alias = result ? wireName : memberName;
            if (alias.equals(name) || !entries.containsKey(alias)) {
                return null;
            }
            if (!result && member.hasTrait(MCP_HEADER_TRAIT) && entries.get(alias) != null) {
                throw new IllegalArgumentException("`" + alias + "` is mirrored into an HTTP header and is only"
                        + " accepted as `" + wireName + "`");
            }
            return alias;
        }

        /**
         * What arguments hold under the member name of a member when its wire name, which is the one that is
         * bound, is there as well.
         */
        Document shadowed(Schema member) {
            var memberName = member.memberName();
            var wireName = McpJson.wireName(member);
            // A member with one name has no other name: its value is the one that is bound.
            return typed
                    || wireName.equals(memberName)
                    || !entries.containsKey(memberName)
                    || !entries.containsKey(wireName)
                            ? null
                            : entries.get(memberName);
        }

        /**
         * Whether arguments hold a value for a member, under a name it is accepted by. Unlike {@link #key},
         * this refuses nothing.
         */
        boolean hasValue(Schema member) {
            // As in key(): a name that is there, even with null, is the one that counts. For a document
            // member null is a value like any other.
            var document = isPlainDocument(member);
            var name = typed ? member.memberName() : McpJson.wireName(member);
            if (entries.containsKey(name)) {
                return document || entries.get(name) != null;
            }
            if (typed || member.hasTrait(MCP_HEADER_TRAIT)) {
                return false;
            }
            return document ? entries.containsKey(member.memberName()) : entries.get(member.memberName()) != null;
        }
    }

    /**
     * Null is a value of a plain document. A document that takes variants holds a variant or is not there.
     */
    private static boolean isPlainDocument(Schema member) {
        var target = member.isMember() ? member.memberTarget() : member;
        return target.type() == ShapeType.DOCUMENT && !target.hasTrait(ONE_OF_TRAIT);
    }

    private static boolean isObject(Document document) {
        return isTyped(document) || document.isType(ShapeType.MAP);
    }

    /**
     * Keeps the properties that the structure does not model, as long as the structure needs no type adaptation:
     * such content has always been passed through as it is. For a typed document these are the members of its
     * own schema that the schema known here lacks, as when a handler is built against a newer model.
     */
    private void copyUnmodeledProperties(
            Document document,
            Schema schema,
            Map<String, Document> converted,
            boolean result,
            Omitted omitted
    ) {
        if (needsTypeAdaptation(schema)) {
            return;
        }
        var target = schema.isMember() ? schema.memberTarget() : schema;
        var discriminator = omitted.discriminator();
        var wireNames = wireNames(target);
        if (isTyped(document)) {
            // The members of the document's own schema that the schema known here lacks. What such a member is
            // called on the wire, and whether it has to be left out, only its own schema knows. That schema is
            // not ours to cache.
            Schema actual = null;
            Set<String> left = null;
            for (var entry : document.asStringMap().entrySet()) {
                var memberName = entry.getKey();
                if (target.member(memberName) != null) {
                    continue;
                }
                if (actual == null) {
                    actual = contents(document).schema;
                    left = actual == null ? Set.of() : omittedMembers(actual, discriminator);
                }
                var member = actual == null ? null : actual.member(memberName);
                var wireName = member == null ? memberName : McpJson.wireName(member);
                // Every name the schema known here uses stays reserved for its own member, set or not.
                if (left.contains(memberName)
                        || memberName.equals(discriminator)
                        || wireName.equals(discriminator)
                        || wireNames.contains(memberName)
                        || wireNames.contains(wireName)
                        || target.member(wireName) != null) {
                    continue;
                }
                converted.put(result ? wireName : memberName, entry.getValue());
            }
            return;
        }
        for (var entry : document.asStringMap().entrySet()) {
            var key = entry.getKey();
            if (target.member(key) == null && !wireNames.contains(key) && !key.equals(discriminator)) {
                converted.put(key, entry.getValue());
            }
        }
    }

    /**
     * Documents backed by a schema ({@code Document.of(shape)}, including its nested structures and unions, and
     * dynamic struct documents) report STRUCTURE or UNION. Schemaless objects (parsed JSON or other protocol
     * documents, {@code Document.of(Map)}) report MAP. Wrappers that report DOCUMENT are resolved by
     * {@link #unwrap} first.
     */
    private static boolean isTyped(Document document) {
        var type = document.type();
        return type == ShapeType.STRUCTURE || type == ShapeType.UNION;
    }

    /**
     * Resolves a document that reports DOCUMENT, such as the value a dynamic structure holds for a modeled
     * document member, to the object it wraps. The type of a document is the type its contents are serialized as,
     * so the contents tell a typed structure from a schemaless map. Anything else is returned as it is.
     */
    static Document unwrap(Document document) {
        if (document.type() != ShapeType.DOCUMENT) {
            return document;
        }
        var contents = contents(document);
        if (contents.struct != null) {
            return contents.struct instanceof Document typed ? typed : Document.of(contents.struct);
        }
        return contents.map ? Document.of(document.asStringMap()) : document;
    }

    /**
     * Returns a document as it is keyed on the wire. A schemaless document already is and is returned as it is.
     * A document backed by a schema is keyed by member name; its structures and unions are copied into maps
     * keyed by wire name, while every other value stays the document it was, so nothing is rounded or reformatted.
     */
    static Document wireKeyed(Document document) {
        if (document == null) {
            return null;
        }
        var value = unwrap(document);
        return isTyped(value) ? rekeyed(value, contents(value).schema) : value;
    }

    /**
     * @param schema Schema of the value. Without one, and for a document member, the value is opaque and stays
     *               the document it is, with the shape it may have.
     */
    private static Document rekeyed(Document value, Schema schema) {
        if (value == null || schema == null) {
            return value;
        }
        return switch (schema.type()) {
            case STRUCTURE, UNION -> {
                if (!isTyped(value)) {
                    yield value;
                }
                var members = new LinkedHashMap<String, Document>();
                for (var entry : value.asStringMap().entrySet()) {
                    var member = schema.member(entry.getKey());
                    members.put(member == null ? entry.getKey() : McpJson.wireName(member),
                            rekeyed(entry.getValue(), member));
                }
                yield Document.of(members);
            }
            case LIST, SET -> {
                var items = new ArrayList<Document>();
                for (var item : value.asList()) {
                    items.add(rekeyed(item, schema.listMember()));
                }
                yield Document.of(items);
            }
            case MAP -> {
                var entries = new LinkedHashMap<String, Document>();
                for (var entry : value.asStringMap().entrySet()) {
                    entries.put(entry.getKey(), rekeyed(entry.getValue(), schema.mapValueMember()));
                }
                yield Document.of(entries);
            }
            default -> value;
        };
    }

    private static ObjectContents contents(Document document) {
        var contents = new ObjectContents();
        try {
            document.serializeContents(contents);
        } catch (RuntimeException e) {
            // Not an object.
        }
        return contents;
    }

    private static final class ObjectContents extends SpecificShapeSerializer {
        private Schema schema;
        private SerializableStruct struct;
        private boolean map;

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            this.schema = schema.isMember() ? schema.memberTarget() : schema;
            this.struct = struct;
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            map = true;
        }
    }

    private Set<String> wireNames(Schema container) {
        return wireNames.computeIfAbsent(container, ignored -> {
            var names = new HashSet<String>();
            for (var member : container.members()) {
                names.add(McpJson.wireName(member));
            }
            return Set.copyOf(names);
        });
    }

    private Document toSmithyOneOf(Document document, Schema schema, boolean passedOn) {
        var targetSchema = schema.isMember() ? schema.memberTarget() : schema;
        var oneOf = targetSchema.getTrait(ONE_OF_TRAIT);
        if (oneOf == null) {
            return document;
        }

        document = unwrap(document);
        if (!isObject(document)) {
            // Nothing in it can name a variant. Asking for a member fails for such a document, as it always has.
            if (!oneOf.getMembers().isEmpty()) {
                document.getMember(oneOf.getMembers().getFirst().getName());
            }
            return document;
        }
        var supplied = Supplied.of(document);
        var discriminator = oneOf.getDiscriminator();
        // Every document is read one way, so that the work stays in proportion to what was sent. A document
        // that reaches the handler as it is and names a variant, by its discriminator or by the shape it is,
        // is that variant's content: keys of it that are called like variants are members of that variant, or
        // nothing the adapter knows, and are not looked at as wrappers.
        var named = passedOn ? namedVariant(document, supplied, oneOf) : null;
        String wrapper = null;
        if (named == null) {
            // A variant the caller supplied counts, whatever it holds. Definitions can share a name.
            for (var member : oneOf.getMembers()) {
                var name = member.getName();
                if (supplied.holds(name) && !name.equals(wrapper)) {
                    if (wrapper != null) {
                        throw new IllegalArgumentException("Document " + targetSchema.id().getName()
                                + " accepts exactly one member, but found `" + wrapper + "` and `" + name + "`");
                    }
                    wrapper = name;
                }
            }
        }
        var content = wrapper == null ? null : supplied.value(wrapper);
        if (content == null && named == null) {
            // Nothing in a wrapper: a document that names a variant is that variant's content already. The
            // other way round, a document that is bound from its wrapper is not also read as the variant a
            // discriminator next to the wrapper may name: nothing of that reaches the handler.
            named = namedVariant(document, supplied, oneOf);
        }
        if (named != null) {
            // It reaches the handler as it is, and is converted all the same, for what the conversion refuses:
            // a handler gets nothing that the wrapped form would not have let through. A variant without
            // renamed, adapted or left-out members refuses nothing and is not read.
            var variantSchema = findSchema(named.getTarget());
            if (variantSchema != null
                    && (variantSchema.type() == ShapeType.STRUCTURE || variantSchema.type() == ShapeType.UNION)
                    && (needsAdaptation(variantSchema)
                            || !omitted(variantSchema, discriminator).members().isEmpty())) {
                convertVariant(document, named, variantSchema, targetSchema, discriminator, true);
            }
            return document;
        }
        if (content == null) {
            return document;
        }
        // What the wrapper holds is bound, and nothing else of the document reaches the handler. Below a
        // document that is passed on, the wrapper is passed on with it and is only converted for what that
        // refuses.
        content = unwrap(content);
        var selected = wrappedVariant(content, wrapper, oneOf, discriminator);
        var converted = new LinkedHashMap<>(convertVariant(
                content,
                selected,
                schemaIndex.getSchema(selected.getTarget()),
                targetSchema,
                discriminator,
                passedOn).asStringMap());
        converted.put(discriminator, Document.of(selected.getTarget().toString()));
        return passedOn ? document : Document.of(converted);
    }

    /**
     * The definition a wrapper name stands for. Definitions that share a name are advertised as alternatives
     * that differ in what they require: content that has what exactly one of them requires is that one's.
     * Otherwise it is the first one's, as it always was.
     */
    private OneOfMember wrappedVariant(Document content, String name, OneOfTrait oneOf, String discriminator) {
        var definitions = oneOf.getMembers().stream().filter(member -> member.getName().equals(name)).toList();
        if (definitions.size() == 1) {
            return definitions.getFirst();
        }
        var matches = definitions.stream()
                .filter(member -> hasRequiredMembers(content, member, discriminator))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : definitions.getFirst();
    }

    /**
     * Whether content has what a variant advertises as required: its required members without those that are
     * left out. This only looks; what the chosen variant refuses, it refuses when it is converted.
     */
    private boolean hasRequiredMembers(Document content, OneOfMember member, String discriminator) {
        var variantSchema = findSchema(member.getTarget());
        if (variantSchema == null || variantSchema.type() != ShapeType.STRUCTURE || !isObject(content)) {
            return false;
        }
        var supplied = Supplied.of(content);
        var omitted = omitted(variantSchema, discriminator).members();
        for (var required : variantSchema.members()) {
            if (required.hasTrait(TraitKey.REQUIRED_TRAIT)
                    && !omitted.contains(required.memberName())
                    && !supplied.hasValue(required)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The variant a document names without a wrapper: by its discriminator, or by the shape a typed structure is.
     */
    private OneOfMember namedVariant(Document document, Supplied supplied, OneOfTrait oneOf) {
        String named = null;
        if (supplied.typed()) {
            var shapeId = resolveVariant(document, oneOf);
            named = shapeId == null ? null : shapeId.toString();
        } else {
            var value = supplied.value(oneOf.getDiscriminator());
            named = value != null && value.isType(ShapeType.STRING) ? value.asString() : null;
        }
        if (named != null) {
            for (var member : oneOf.getMembers()) {
                if (member.getTarget().toString().equals(named)) {
                    return member;
                }
            }
        }
        return null;
    }

    private Document convertVariant(
            Document content,
            OneOfMember member,
            Schema variantSchema,
            Schema document,
            String discriminator,
            boolean passedOn
    ) {
        // The discriminator key is reserved. Check it on what was sent: projecting the content can drop the key.
        if (content.isType(ShapeType.MAP)) {
            requireVariant(content.asStringMap(), discriminator, member.getTarget().toString(), document);
        }
        return variantSchema.type() == ShapeType.STRUCTURE
                ? structureToSmithy(content, variantSchema, true, omitted(variantSchema, discriminator), passedOn)
                : toSmithy(content, variantSchema, true, passedOn);
    }

    private static void requireVariant(Map<String, Document> sent, String key, String variant, Schema document) {
        var value = sent.get(key);
        if (value != null && !(value.isType(ShapeType.STRING) && value.asString().equals(variant))) {
            throw new IllegalArgumentException("`" + key + "` is the discriminator of " + document.id().getName()
                    + " and can only be `" + variant + "` here");
        }
    }

    /**
     * The members of a structure or union that are not exposed, by member name: every member whose wire name is
     * the member name of another member, and that other member; and all members that share one wire name.
     *
     * <p>A key with such a name means one of the two in a document keyed by member name (what a handler writes,
     * and what callers sent before members were named by their wire name) and the other one in a document keyed
     * by wire name (what callers send now, and what a backend may return). No reading is right for both, so
     * neither member is advertised, bound from arguments under any name, or emitted in results. Among the
     * members that remain, no key names two of them.
     */
    static Set<String> overlappingMembers(Schema container) {
        var target = container.isMember() ? container.memberTarget() : container;
        if (target.type() != ShapeType.STRUCTURE && target.type() != ShapeType.UNION) {
            return Set.of();
        }
        Set<String> names = null;
        var owners = new HashMap<String, String>();
        for (var member : target.members()) {
            var memberName = member.memberName();
            var wireName = McpJson.wireName(member);
            // Two members with one wire name: only possible in a model that was not validated.
            var other = owners.put(wireName, memberName);
            if (other != null) {
                names = names == null ? new HashSet<>() : names;
                names.add(memberName);
                names.add(other);
            }
            if (!wireName.equals(memberName) && target.member(wireName) != null) {
                names = names == null ? new HashSet<>() : names;
                names.add(memberName);
                names.add(wireName);
            }
        }
        return names == null ? Set.of() : Set.copyOf(names);
    }

    /**
     * The members of a {@code @oneOf} variant that are not exposed: {@link #overlappingMembers}, and for a
     * structure also every member that goes by the name of the discriminator, in the model or on the wire,
     * because flattened content cannot hold it next to the discriminator.
     *
     * @param discriminator Discriminator of the document, or null for a shape that is not used as a variant.
     */
    static Set<String> omittedMembers(Schema variant, String discriminator) {
        var target = variant.isMember() ? variant.memberTarget() : variant;
        var overlapping = overlappingMembers(target);
        if (discriminator == null || target.type() != ShapeType.STRUCTURE) {
            return overlapping;
        }
        Set<String> names = null;
        for (var member : target.members()) {
            if (member.memberName().equals(discriminator) || McpJson.wireName(member).equals(discriminator)) {
                names = names == null ? new HashSet<>(overlapping) : names;
                names.add(member.memberName());
            }
        }
        return names == null ? overlapping : Set.copyOf(names);
    }

    /**
     * Cached for the schemas of this adapter's index.
     */
    private Omitted omitted(Schema container, String discriminator) {
        var target = container.isMember() ? container.memberTarget() : container;
        if (discriminator == null) {
            return overlapping.computeIfAbsent(target,
                    ignored -> new Omitted(null, overlappingMembers(target)));
        }
        return omittedFromVariant.computeIfAbsent(target, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(discriminator,
                        ignored -> new Omitted(discriminator, omittedMembers(target, discriminator)));
    }

    /**
     * @param discriminator Discriminator of the document the structure is a variant of, or null elsewhere.
     * @param members Names of the members that are left out.
     */
    private record Omitted(String discriminator, Set<String> members) {}

    /**
     * A result selects a union member that is not exposed, so it cannot be expressed.
     */
    static final class UnavailableResultException extends RuntimeException {
        UnavailableResultException(String message) {
            super(message);
        }
    }

    private Document fromSmithyOneOf(Document document, Schema schema) {
        var targetSchema = schema.isMember() ? schema.memberTarget() : schema;
        var oneOf = targetSchema.getTrait(ONE_OF_TRAIT);
        if (oneOf == null) {
            return document;
        }

        document = unwrap(document);
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
                var source = document;
                if (!isTyped(document)) {
                    // Only an untyped document carries the discriminator as a key. It is not content: drop it
                    // before projecting the rest onto the variant.
                    var content = new LinkedHashMap<>(document.asStringMap());
                    content.remove(oneOf.getDiscriminator());
                    source = Document.of(content);
                }
                var discriminator = oneOf.getDiscriminator();
                var converted = new LinkedHashMap<>((variantSchema.type() == ShapeType.STRUCTURE
                        ? structureFromSmithy(source, variantSchema, true, omitted(variantSchema, discriminator))
                        : fromSmithy(source, variantSchema, true)).asStringMap());
                // The key is never content, whatever a variant that is not a structure holds.
                converted.remove(discriminator);
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
        if (!isTyped(document)) {
            var discriminator = document.getMember(oneOf.getDiscriminator());
            return discriminator == null ? null : ShapeId.from(discriminator.asString());
        }
        // A typed structure reports its own shape ID. A member of it that has the name of the discriminator is
        // content. Unions stay untagged.
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

    /**
     * Whether values of the schema contain a type that has a different MCP representation, or a renamed member.
     */
    private boolean needsAdaptation(Schema schema) {
        return needsAdaptation(adaptationRequired, schema, Adapted.RENAMES);
    }

    /**
     * Whether values of the schema contain a type that has a different MCP representation. Renamed members
     * alone do not count: typed documents are serialized with their wire names by the codec.
     */
    private boolean needsTypeAdaptation(Schema schema) {
        return needsAdaptation(typeAdaptationRequired, schema, Adapted.TYPES);
    }

    /**
     * Whether a typed result has to be converted: it contains a type that has a different MCP representation,
     * or a structure with members that are left out, which the codec would write.
     */
    private boolean needsResultAdaptation(Schema schema) {
        return needsAdaptation(resultAdaptationRequired, schema, Adapted.TYPES_AND_OMISSIONS);
    }

    private boolean needsAdaptation(Map<Schema, Boolean> cache, Schema schema, Adapted adapted) {
        // The answer belongs to the shape, however it was reached: members that share a target share it.
        var target = schema.isMember() ? schema.memberTarget() : schema;
        var known = cache.get(target);
        if (known != null) {
            return known;
        }
        Set<Schema> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var result = needsAdaptation(target, adapted, visited, cache);
        if (!result) {
            // Nothing was found from here, so nothing can be found from any shape that was reached.
            for (var reached : visited) {
                cache.put(reached, false);
            }
        }
        return result;
    }

    private enum Adapted {
        RENAMES,
        TYPES,
        TYPES_AND_OMISSIONS
    }

    /**
     * Whether something that needs adaptation can be reached from a schema. Each shape is looked at once: a
     * shape that was reached before has been searched, or is being searched further up, and adds nothing. A
     * shape from which something was found is remembered, so that no later search enters it again.
     */
    private boolean needsAdaptation(Schema schema, Adapted adapted, Set<Schema> visited, Map<Schema, Boolean> cache) {
        var target = schema.isMember() ? schema.memberTarget() : schema;
        var known = cache.get(target);
        if (known != null) {
            return known;
        }
        if (!visited.add(target)) {
            return false;
        }
        shapesSearched.incrementAndGet();
        var result = switch (target.type()) {
            case BIG_DECIMAL, BIG_INTEGER, BLOB, TIMESTAMP, DOCUMENT, UNION -> true;
            case STRUCTURE -> {
                if (adapted == Adapted.TYPES_AND_OMISSIONS && !omitted(target, null).members().isEmpty()) {
                    yield true;
                }
                for (var member : target.members()) {
                    if ((adapted == Adapted.RENAMES && !McpJson.wireName(member).equals(member.memberName()))
                            || needsAdaptation(member, adapted, visited, cache)) {
                        yield true;
                    }
                }
                yield false;
            }
            case LIST, SET -> needsAdaptation(target.listMember(), adapted, visited, cache);
            case MAP -> needsAdaptation(target.mapValueMember(), adapted, visited, cache);
            default -> false;
        };
        if (result) {
            cache.put(target, true);
        }
        return result;
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
