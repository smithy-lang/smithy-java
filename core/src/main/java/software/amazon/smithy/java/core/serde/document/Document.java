/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A Smithy document type, representing untyped data from the Smithy data model.
 *
 * <h3>Documents and the Smithy data model</h3>
 *
 * <p>The Smithy data model consists of:
 *
 * <ul>
 *     <li>Numbers: byte, short, integer, long, float, double, bigInteger, bigDecimal. IntEnum shapes are
 *         represented as integers in the Smithy data model.</li>
 *     <li>boolean</li>
 *     <li>blob</li>
 *     <li>string: enum shapes are represented as strings in the Smithy data model</li>
 *     <li>timestamp: Represented as an {@link Instant}</li>
 *     <li>list: list of Documents</li>
 *     <li>map: map of int|long|string keys to Document values</li>
 *     <li>struct: structure or union</li>
 * </ul>
 *
 * <h3>Serializing documents and their contents</h3>
 *
 * <p>A document type implements {@link SerializableShape} and implementations must always call
 * {@link ShapeSerializer#writeDocument}. Shape serializers can access the contents of the document using
 * {@link #serializeContents}, which emits the Smithy data model based on the contents of the document.
 * The first shape written with {@link #serializeContents} must not be a document because doing so would cause
 * infinite recursion in serializers.
 *
 * <h3>Protocol smoothing, string and blob interop</h3>
 *
 * <p>Document types are a protocol-agnostic view of untyped data. Protocol codecs should attempt to smooth over
 * protocol incompatibilities with the Smithy data model. If a protocol serializes a blob as a base64 encoded string,
 * then calling {@link #asBlob()} should automatically base64 decode the value and return the underlying bytes.
 *
 * <h3>Typed documents</h3>
 *
 * <p>Document types can be combined with a typed schema using {@link #of(SerializableShape)}. This kind of
 * document type allows (but does not require) codecs to serialize or deserialize the document exactly as if the shape
 * itself was serialized or deserialized directly.
 */
public interface Document extends SerializableShape {
    /**
     * Get the Smithy data model type for the underlying contents of the document.
     *
     * <p>The type can be used to know the appropriate "as" method to call to get the underlying data of the document.
     *
     * <p>The type returned from this method will differ from the type and schema emitted from
     * {@link #serialize(ShapeSerializer)}, which always writes the document as {@link ShapeSerializer#writeDocument}.
     * However, the type returned from this method should correspond to the type emitted from
     * {@link #serializeContents(ShapeSerializer)}.
     *
     * <ul>
     *     <li>enum shapes: Enum shapes are treated as a {@link ShapeType#STRING}, and variants can be found in
     *     the corresponding schema emitted from {@link #serializeContents(ShapeSerializer)}.</li>
     *     <li>intEnum shapes: Enum shapes are treated as an {@link ShapeType#INTEGER}, and variants can be found in
     *     the corresponding schema emitted from {@link #serializeContents(ShapeSerializer)}.</li>
     * </ul>
     *
     * @return the Smithy data model type.
     */
    ShapeType type();

    /**
     * Attempts to find and parse a shape ID from the document in the document's discriminator field.
     *
     * <p>Typed documents must return the shape ID of the enclosed shape. When possible, document implementations
     * should account for protocol-specific differences in how a discriminator is serialized. For example, a JSON codec
     * should override this method, check __type, and parse a shape ID when this is called.
     *
     * @return the non-null, parsed shape ID.
     * @throws DiscriminatorException if the document doesn't have a valid discriminator.
     */
    default ShapeId discriminator() {
        throw new DiscriminatorException(type() + " document has no discriminator");
    }

    /**
     * Serializes the Document as a document value in the Smithy data model.
     *
     * <p>All implementations of a document type are expected to follow the same behavior as this method when writing
     * to a {@link ShapeSerializer}; the document is always written with {@link ShapeSerializer#writeDocument(Schema, Document)}
     * and receivers are free to query the underlying contents of the document using
     * {@link #serializeContents(ShapeSerializer)}.
     *
     * @param serializer Where to send the document to {@link ShapeSerializer#writeDocument(Schema, Document)}.
     */
    @Override
    default void serialize(ShapeSerializer serializer) {
        serializer.writeDocument(PreludeSchemas.DOCUMENT, this);
    }

    /**
     * Serialize the contents of the document using the Smithy data model and an appropriate {@link javax.xml.validation.Schema}.
     *
     * <p>While {@link #serialize(ShapeSerializer)} always emits document values as
     * {@link ShapeSerializer#writeDocument(Schema, Document)}, this method emits the contents of the document itself.
     * {@code ShapeSerializer} implementations that receive a {@link Document} via {@code writeDocument} can get the
     * inner contents of the document using this method.
     *
     * <p>When implementing this method, each call to the serializer must provide the most appropriate schema
     * possible to capture the underlying document value. Documents that are not typed to a specific shape should use
     * schemas from {@link PreludeSchemas}. For example, a string document should use {@link PreludeSchemas#STRING},
     * and an integer document should use {@link PreludeSchemas#INTEGER}.
     *
     * <p>Structure, list, and map documents that are not typed to a specific shape should use
     * {@link PreludeSchemas#DOCUMENT}. Members emitted for these shapes must include an appropriate member name
     * and target shape. For example, to represent the value of a map entry, the value must be emitted as a member
     * schema (even a synthetic member) with a member name of "value" and the document value target
     * (e.g., {@code smithy.api#Document$value}). In doing so, receivers of the document's data model do not need to
     * implement special-cased logic to account for synthetic document type members vs. actual modeled members
     *
     * <p>Implementations must not write the contents of the document as {@link ShapeSerializer#writeDocument(Schema, Document)}
     * because that could result in infinite recursion for serializers that want access to the contents of a document.
     *
     * @param serializer Serializer to write the underlying data of the document to.
     */
    void serializeContents(ShapeSerializer serializer);

    /**
     * Get the boolean value of the Document if it is a boolean.
     *
     * @return the boolean value.
     * @throws SerializationException if the Document is not a boolean.
     */
    default boolean asBoolean() {
        throw new SerializationException("Expected a boolean document, but found " + type());
    }

    /**
     * Get the byte value of the Document if it is a byte.
     *
     * <p>Numeric values of a different type are cast when necessary. See JLS 5.1.3 for details.
     *
     * @return the byte value.
     * @throws SerializationException if the Document is not a number.
     */
    default byte asByte() {
        throw new SerializationException("Expected a byte document, but found " + type());
    }

    /**
     * Get the short value of the Document if it is a short.
     *
     * <p>Numeric values of a different type are cast when necessary. See JLS 5.1.3 for details.
     *
     * @return the short value.
     * @throws SerializationException if the Document is not a number.
     */
    default short asShort() {
        throw new SerializationException("Expected a short document, but found " + type());
    }

    /**
     * Get the integer value of the Document if it is an integer.
     *
     * <p>Numeric values of a different type are cast when necessary. See JLS 5.1.3 for details.
     *
     * @return the integer value.
     * @throws SerializationException if the Document is not a number.
     */
    default int asInteger() {
        throw new SerializationException("Expected an integer document, but found " + type());
    }

    /**
     * Get the long value of the Document if it is a long.
     *
     * <p>Numeric values of a different type are cast when necessary. See JLS 5.1.3 for details.
     *
     * @return the long value.
     * @throws SerializationException if the Document is not a number.
     */
    default long asLong() {
        throw new SerializationException("Expected a long document, but found " + type());
    }

    /**
     * Get the float value of the Document if it is a float.
     *
     * <p>Numeric values of a different type are cast when necessary. See JLS 5.1.3 for details.
     *
     * @return the float value.
     * @throws SerializationException if the Document is not a number.
     */
    default float asFloat() {
        throw new SerializationException("Expected a float document, but found " + type());
    }

    /**
     * Get the double value of the Document if it is a double.
     *
     * <p>Numeric values of a different type are cast when necessary. See JLS 5.1.3 for details.
     *
     * @return the double value.
     * @throws SerializationException if the Document is not a number.
     */
    default double asDouble() {
        throw new SerializationException("Expected a double document, but found " + type());
    }

    /**
     * Get the BigInteger value of the Document if it is a bigInteger.
     *
     * <p>Numeric values of a different type are cast when necessary. See JLS 5.1.3 for details.
     *
     * @return the BigInteger value.
     * @throws SerializationException if the Document is not a number.
     */
    default BigInteger asBigInteger() {
        throw new SerializationException("Expected a bigInteger document, but found " + type());
    }

    /**
     * Get the BigDecimal value of the Document if it is a bigDecimal.
     *
     * <p>Numeric values of a different type are cast when necessary. See JLS 5.1.3 for details.
     *
     * @return the BigDecimal value.
     * @throws SerializationException if the Document is not a number.
     */
    default BigDecimal asBigDecimal() {
        throw new SerializationException("Expected a bigDecimal document, but found " + type());
    }

    /**
     * Get the value of the builder as a Number.
     *
     * @return the Number value.
     * @throws SerializationException if the Document is not a numeric type.
     */
    default Number asNumber() {
        return switch (type()) {
            case BYTE -> asByte();
            case SHORT -> asShort();
            case INTEGER -> asInteger();
            case LONG -> asLong();
            case FLOAT -> asFloat();
            case DOUBLE -> asDouble();
            case BIG_INTEGER -> asBigInteger();
            case BIG_DECIMAL -> asBigDecimal();
            default -> throw new SerializationException("Expected a number document, but found " + type());
        };
    }

    /**
     * Get the string value of the Document if it is a string.
     *
     * @return the string value.
     * @throws SerializationException if the Document is not a string.
     */
    default String asString() {
        throw new SerializationException("Expected a string document, but found " + type());
    }

    /**
     * Get the Document as a blob if the Document is a blob.
     *
     * @return the bytes of the blob.
     * @throws SerializationException if the Document is not a blob.
     */
    default ByteBuffer asBlob() {
        throw new SerializationException("Expected a blob document, but found " + type());
    }

    /**
     * Get the Document as an Instant if the Document is a timestamp.
     *
     * @return the Instant value of the timestamp.
     * @throws SerializationException if the Document is not a timestamp.
     */
    default Instant asTimestamp() {
        throw new SerializationException("Expected a timestamp document, but found " + type());
    }

    /**
     * Get the number of elements in an array document, or the number of key value pairs in a map document.
     *
     * @return the number of elements. Defaults to -1 for all other documents.
     */
    default int size() {
        return -1;
    }

    /**
     * Get the list contents of the Document if it is a list.
     *
     * @return the list contents.
     * @throws SerializationException if the Document is not a list.
     */
    default List<Document> asList() {
        throw new SerializationException("Expected a list document, but found " + type());
    }

    /**
     * Get the Document as a map of strings to Documents if the Document is a map, a structure, or union.
     *
     * <p>If the Document is a map, and the keys are strings the map entries are returned. If the document is a
     * structure or union, the members of the structure or union are returned as a map where the keys are the
     * member names of each present member.
     *
     * @return the map contents.
     * @throws SerializationException if the Document is not a map, structure, or union or the keys are numbers.
     */
    default Map<String, Document> asStringMap() {
        throw new SerializationException("Expected a map of strings to documents, but found " + type());
    }

    /**
     * Unwrap the document and convert to a standard library type compatible with {@link #ofObject(Object)}.
     *
     * @return the unwrapped document value.
     */
    default Object asObject() {
        return switch (type()) {
            case BLOB -> asBlob();
            case BOOLEAN -> asBoolean();
            case STRING, ENUM -> asString();
            case TIMESTAMP -> asTimestamp();
            case BYTE -> asByte();
            case SHORT -> asShort();
            case INTEGER, INT_ENUM -> asInteger();
            case LONG -> asLong();
            case FLOAT -> asFloat();
            case DOUBLE -> asDouble();
            case BIG_DECIMAL -> asBigDecimal();
            case BIG_INTEGER -> asBigInteger();
            case LIST, SET -> {
                var elements = asList();
                List<Object> result = new ArrayList<>(elements.size());
                for (var e : elements) {
                    if (e == null) {
                        result.add(null);
                    } else {
                        result.add(e.asObject());
                    }
                }
                yield result;
            }
            case MAP, STRUCTURE, UNION -> {
                var elements = asStringMap();
                Map<String, Object> result = new LinkedHashMap<>(elements.size());
                for (var e : elements.entrySet()) {
                    var value = e.getValue();
                    if (value == null) {
                        result.put(e.getKey(), null);
                    } else {
                        result.put(e.getKey(), value.asObject());
                    }
                }
                yield result;
            }
            default -> throw new UnsupportedOperationException("Unable to convert document to object: " + this);
        };
    }

    /**
     * Get a map, struct, or union member from the Document by name.
     *
     * @param memberName Member to access from the Document. For Document types with a schema, this name is the name
     *                   of the member defined in a Smithy model.
     * @return the member, or null if not found.
     * @throws IllegalStateException if the Document is not a string map, structure, or union shape.
     */
    default Document getMember(String memberName) {
        throw new SerializationException("Expected a map, structure, or union document, but found " + type());
    }

    /**
     * List all the available members.
     */
    default Set<String> getMemberNames() {
        throw new SerializationException("Expected a map, structure, or union document, but found " + type());
    }

    /**
     * Create a deserializer for the document.
     *
     * @return the deserializer.
     */
    default ShapeDeserializer createDeserializer() {
        return new DocumentDeserializer(this);
    }

    /**
     * Attempt to deserialize the Document into a builder.
     *
     * @param builder Builder to populate from the Document.
     * @param <T> Shape type to build.
     */
    default <T extends SerializableShape> void deserializeInto(ShapeBuilder<T> builder) {
        builder.deserialize(createDeserializer());
    }

    /**
     * Attempt to deserialize the Document into a builder and create the shape.
     *
     * @param builder Builder to populate from the Document.
     * @return the built and error-corrected shape.
     * @param <T> Shape type to build.
     */
    default <T extends SerializableShape> T asShape(ShapeBuilder<T> builder) {
        deserializeInto(builder);
        return builder.errorCorrection().build();
    }

    /**
     * Create a number document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document ofNumber(Number value) {
        return new Documents.NumberDocument(DocumentUtils.getSchemaForNumber(value), value);
    }

    /**
     * Create a byte Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(byte value) {
        return new Documents.NumberDocument(PreludeSchemas.BYTE, value);
    }

    /**
     * Create a short Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(short value) {
        return new Documents.NumberDocument(PreludeSchemas.SHORT, value);
    }

    /**
     * Create an integer Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(int value) {
        return new Documents.NumberDocument(PreludeSchemas.INTEGER, value);
    }

    /**
     * Create a long Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(long value) {
        return new Documents.NumberDocument(PreludeSchemas.LONG, value);
    }

    /**
     * Create a float Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(float value) {
        return new Documents.NumberDocument(PreludeSchemas.FLOAT, value);
    }

    /**
     * Create a double Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(double value) {
        return new Documents.NumberDocument(PreludeSchemas.DOUBLE, value);
    }

    /**
     * Create a bigInteger Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(BigInteger value) {
        return new Documents.NumberDocument(PreludeSchemas.BIG_INTEGER, value);
    }

    /**
     * Create a bigDecimal Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(BigDecimal value) {
        return new Documents.NumberDocument(PreludeSchemas.BIG_DECIMAL, value);
    }

    /**
     * Create a boolean Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(boolean value) {
        return new Documents.BooleanDocument(PreludeSchemas.BOOLEAN, value);
    }

    /**
     * Create a string Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(String value) {
        return new Documents.StringDocument(PreludeSchemas.STRING, value);
    }

    /**
     * Create a blob Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(ByteBuffer value) {
        return new Documents.BlobDocument(PreludeSchemas.BLOB, value);
    }

    static Document of(byte[] value) {
        return of(ByteBuffer.wrap(value));
    }

    /**
     * Create a timestamp Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(Instant value) {
        return new Documents.TimestampDocument(PreludeSchemas.TIMESTAMP, value);
    }

    /**
     * Create a list Document.
     *
     * @param values Values to wrap.
     * @return the Document type.
     */
    static Document of(List<Document> values) {
        return new Documents.ListDocument(Documents.LIST_SCHEMA, values);
    }

    /**
     * Create a Document for a map of strings to Documents.
     *
     * @param members Members to wrap.
     * @return the Document type.
     */
    static Document of(Map<String, Document> members) {
        return new Documents.StringMapDocument(Documents.STR_MAP_SCHEMA, members);
    }

    /**
     * Create a document from an object that can be one of a number of standard library Java types.
     *
     * <p>This method supports the following conversions only. Reflection-based creation is not supported.
     *
     * <ul>
     *     <li>{@link Document}</li>
     *     <li>{@link SerializableShape}</li>
     *     <li>{@link String}</li>
     *     <li>{@code byte[]} to blob</li>
     *     <li>{@link Instant} to timestamp</li>
     *     <li>{@link Boolean}</li>
     *     <li>{@link Byte}</li>
     *     <li>{@link Short}</li>
     *     <li>{@link Integer}</li>
     *     <li>{@link Long}</li>
     *     <li>{@link Float}</li>
     *     <li>{@link Double}</li>
     *     <li>{@link BigInteger}</li>
     *     <li>{@link BigDecimal}</li>
     *     <li>{@link List} of convertable objects</li>
     *     <li>{@link Map} of string to convertable objects</li>
     * </ul>
     *
     * @param o Object to convert to a document.
     * @return the created document.
     * @throws UnsupportedOperationException if the given object {@code o} cannot be converted to a document.
     */
    static Document ofObject(Object o) {
        if (o instanceof Document d) {
            return d;
        } else if (o instanceof SerializableShape s) {
            return of(s);
        } else if (o instanceof String s) {
            return of(s);
        } else if (o instanceof Boolean b) {
            return of(b);
        } else if (o instanceof Number n) {
            return ofNumber(n);
        } else if (o instanceof ByteBuffer b) {
            return of(b);
        } else if (o instanceof byte[] b) {
            return of(b);
        } else if (o instanceof Instant i) {
            return of(i);
        } else if (o instanceof List<?> l) {
            List<Document> values = new ArrayList<>(l.size());
            for (var v : l) {
                values.add(ofObject(v));
            }
            return of(values);
        } else if (o instanceof Map<?, ?> m) {
            Map<String, Document> values = new LinkedHashMap<>(m.size());
            for (var entry : m.entrySet()) {
                var key = ofObject(entry.getKey());
                values.put(key.asString(), ofObject(entry.getValue()));
            }
            return of(values);
        } else if (o == null) {
            return null;
        } else {
            throw new UnsupportedOperationException("Cannot convert " + o + " to a document");
        }
    }

    /**
     * Create a Document from a {@link SerializableShape}.
     *
     * <p>The created document is <em>typed</em> and captures the state of the shape exactly; meaning if the document
     * is serialized or deserialized by a codec, it'd done so exactly as if the underlying shape was serialized or
     * deserialized.
     *
     * @param shape Shape to turn into a Document.
     * @return the Document type.
     */
    static Document of(SerializableShape shape) {
        var parser = new DocumentParser();
        shape.serialize(parser);
        return parser.getResult();
    }

    /**
     * Determines if two documents are equal, ignoring schemas and protocol details.
     *
     * @param left  Left document to compare.
     * @param right Right document to compare.
     * @return true if they are equal.
     */
    static boolean equals(Object left, Object right) {
        return equals(left, right, 0);
    }

    /**
     * Determines if two documents are equal, ignoring schemas and protocol details.
     *
     * @param left  Left document to compare.
     * @param right Right document to compare.
     * @param options A bitfield of flags OR'd together from {@link DocumentEqualsFlags}.
     * @return true if they are equal.
     */
    static boolean equals(Object left, Object right, int options) {
        if (left instanceof Document l && right instanceof Document r) {
            if (l == r) {
                return true;
            }
            System.out.println("LEFT " + l);
            System.out.println("RIGHT " + r);
            return switch (l.type()) {
                case BLOB -> l.type() == r.type() && l.asBlob().equals(r.asBlob());
                case BOOLEAN -> l.type() == r.type() && l.asBoolean() == r.asBoolean();
                case STRING, ENUM -> l.type() == r.type() && l.asString().equals(r.asString());
                case TIMESTAMP -> l.type() == r.type() && l.asTimestamp().equals(r.asTimestamp());
                case BYTE -> (options & DocumentEqualsFlags.NUMBER_PROMOTION) != 0
                        ? DocumentUtils.numberEquals(l.asByte(), r)
                        : l.type() == r.type() && l.asByte() == r.asByte();
                case SHORT -> (options & DocumentEqualsFlags.NUMBER_PROMOTION) != 0
                        ? DocumentUtils.numberEquals(l.asShort(), r)
                        : l.type() == r.type() && l.asShort() == r.asShort();
                case INTEGER, INT_ENUM -> (options & DocumentEqualsFlags.NUMBER_PROMOTION) != 0
                        ? DocumentUtils.numberEquals(l.asInteger(), r)
                        : l.type() == r.type() && l.asInteger() == r.asInteger();
                case LONG -> (options & DocumentEqualsFlags.NUMBER_PROMOTION) != 0
                        ? DocumentUtils.numberEquals(l.asLong(), r)
                        : l.type() == r.type() && l.asLong() == r.asLong();
                case FLOAT -> (options & DocumentEqualsFlags.NUMBER_PROMOTION) != 0
                        ? DocumentUtils.numberEquals(l.asFloat(), r)
                        : l.type() == r.type() && l.asFloat() == r.asFloat();
                case DOUBLE -> (options & DocumentEqualsFlags.NUMBER_PROMOTION) != 0
                        ? DocumentUtils.numberEquals(l.asDouble(), r)
                        : l.type() == r.type() && l.asDouble() == r.asDouble();
                case BIG_DECIMAL -> (options & DocumentEqualsFlags.NUMBER_PROMOTION) != 0
                        ? DocumentUtils.numberEquals(l.asBigDecimal(), r)
                        : l.type() == r.type() && l.asBigDecimal()
                                .stripTrailingZeros()
                                .equals(r.asBigDecimal().stripTrailingZeros());
                case BIG_INTEGER -> (options & DocumentEqualsFlags.NUMBER_PROMOTION) != 0
                        ? DocumentUtils.numberEquals(l.asBigInteger(), r)
                        : l.type() == r.type() && l.asBigInteger().equals(r.asBigInteger());
                case LIST, SET -> {
                    if (l.type() != r.type()) {
                        yield false;
                    }
                    var ll = l.asList();
                    var rl = r.asList();
                    if (ll.size() != rl.size()) {
                        yield false;
                    }
                    for (int i = 0; i < ll.size(); i++) {
                        if (!equals(ll.get(i), rl.get(i), options)) {
                            yield false;
                        }
                    }
                    yield true;
                }
                case MAP, STRUCTURE, UNION -> {
                    if (l.type() != r.type()) {
                        yield false;
                    }
                    var lm = l.asStringMap();
                    var rm = r.asStringMap();
                    if (lm.size() != rm.size()) {
                        yield false;
                    }
                    for (var entry : lm.entrySet()) {
                        if (!equals(entry.getValue(), rm.get(entry.getKey()), options)) {
                            yield false;
                        }
                    }
                    yield true;
                }
                default -> false; // unexpected type (DOCUMENT, MEMBER, OPERATION, SERVICE).
            };
        }
        return false;
    }

    /**
     * Compares two Documents.
     *
     * <p>If the documents are numbers, then they are compared using JLS 5.1.2 type promotion.
     * Strings documents are compared lexicographically.
     *
     * <p><strong>Note:</strong> Only numeric and string types are supported.
     *
     * @param left Left document to compare.
     * @param right Right document to compare.
     * @throws SerializationException if the document types are not comparable.
     * @return the value {@code 0} if {@code left == right} a value less than {@code 0} if {@code left < right}
     *         and a value greater than {@code 0} if {@code left > right}.
     */
    static int compare(Document left, Document right) {
        if (left == right) {
            return 0;
        }
        return switch (left.type()) {
            case TIMESTAMP -> left.asTimestamp().compareTo(right.asTimestamp());
            case BYTE -> DocumentUtils.compareWithPromotion(left.asByte(), right);
            case SHORT -> DocumentUtils.compareWithPromotion(left.asShort(), right);
            case INTEGER, INT_ENUM -> DocumentUtils.compareWithPromotion(left.asInteger(), right);
            case LONG -> DocumentUtils.compareWithPromotion(left.asLong(), right);
            case FLOAT -> DocumentUtils.compareWithPromotion(left.asFloat(), right);
            case DOUBLE -> DocumentUtils.compareWithPromotion(left.asDouble(), right);
            case BIG_DECIMAL -> DocumentUtils.compareWithPromotion(left.asBigDecimal(), right);
            case BIG_INTEGER -> DocumentUtils.compareWithPromotion(left.asBigInteger(), right);
            case STRING -> left.asString().compareTo(right.asString());
            default -> throw new SerializationException("Could not compare documents of type "
                    + left.type() + " and " + right.type());
        };
    }
}
