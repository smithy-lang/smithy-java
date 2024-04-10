/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.xml.validation.Schema;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
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
 * Conversely, if a document contains binary encoded data and {@link #asString()} is called, the document should
 * automatically attempt to return a UTF-8 string from the bytes.
 *
 * <h3>Typed documents</h3>
 *
 * <p>Document types can be combined with a typed schema using {@link #ofStruct(SerializableShape)}. This kind of
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
     * Serializes the Document as a document value in the Smithy data model.
     *
     * <p>All implementations of a document type are expected to follow the same behavior as this method when writing
     * to a {@link ShapeSerializer}; the document is always written with {@link ShapeSerializer#writeDocument(Document)}
     * and receivers are free to query the underlying contents of the document using
     * {@link #serializeContents(ShapeSerializer)}.
     *
     * @param serializer Where to send the document to {@link ShapeSerializer#writeDocument(Document)}.
     */
    @Override
    default void serialize(ShapeSerializer serializer) {
        serializer.writeDocument(this);
    }

    /**
     * Serialize the contents of the document using the Smithy data model and an appropriate {@link Schema}.
     *
     * <p>While {@link #serialize(ShapeSerializer)} always emits document values as
     * {@link ShapeSerializer#writeDocument(Document)}, this method emits the contents of the document itself.
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
     * implement special-cased logic to account for synthetic document type members vs actual modeled members
     *
     * <p>Implementations must not write the conents of the document as {@link ShapeSerializer#writeDocument(Document)},
     * because that could result in infinite recursion for serializers that want access to the contents of a document.
     *
     * @param serializer Serializer to write the underlying data of the document to.
     */
    void serializeContents(ShapeSerializer serializer);

    /**
     * Get the boolean value of the Document if it is a boolean.
     *
     * @return the boolean value.
     * @throws SdkSerdeException if the Document is not a boolean.
     */
    default boolean asBoolean() {
        throw new SdkSerdeException("Expected a boolean document, but found " + type());
    }

    /**
     * Get the byte value of the Document if it is a byte.
     *
     * <p>If the value is a number of a different type, the value is cast, which can result in a loss of precision.
     *
     * @return the byte value.
     * @throws SdkSerdeException if the Document is not a number.
     */
    default byte asByte() {
        throw new SdkSerdeException("Expected a byte document, but found " + type());
    }

    /**
     * Get the short value of the Document if it is a short.
     *
     * <p>If the value is a number of a different type, the value is cast, which can result in a loss of precision.
     *
     * @return the short value.
     * @throws SdkSerdeException if the Document is not a number.
     */
    default short asShort() {
        throw new SdkSerdeException("Expected a short document, but found " + type());
    }

    /**
     * Get the integer value of the Document if it is an integer.
     *
     * <p>If the value is a number of a different type, the value is cast, which can result in a loss of precision.
     *
     * @return the integer value.
     * @throws SdkSerdeException if the Document is not a number.
     */
    default int asInteger() {
        throw new SdkSerdeException("Expected an integer document, but found " + type());
    }

    /**
     * Get the long value of the Document if it is a long.
     *
     * <p>If the value is a number of a different type, the value is cast, which can result in a loss of precision.
     *
     * @return the long value.
     * @throws SdkSerdeException if the Document is not a number.
     */
    default long asLong() {
        throw new SdkSerdeException("Expected a long document, but found " + type());
    }

    /**
     * Get the float value of the Document if it is a float.
     *
     * <p>If the value is a number of a different type, the value is cast, which can result in a loss of precision.
     *
     * @return the float value.
     * @throws SdkSerdeException if the Document is not a number.
     */
    default float asFloat() {
        throw new SdkSerdeException("Expected a float document, but found " + type());
    }

    /**
     * Get the double value of the Document if it is a double.
     *
     * <p>If the value is a number of a different type, the value is cast, which can result in a loss of precision.
     *
     * @return the double value.
     * @throws SdkSerdeException if the Document is not a number.
     */
    default double asDouble() {
        throw new SdkSerdeException("Expected a double document, but found " + type());
    }

    /**
     * Get the BigInteger value of the Document if it is a bigInteger.
     *
     * <p>If the value is a number of a different type, the value is cast, which can result in a loss of precision.
     *
     * @return the BigInteger value.
     * @throws SdkSerdeException if the Document is not a number.
     */
    default BigInteger asBigInteger() {
        throw new SdkSerdeException("Expected a bigInteger document, but found " + type());
    }

    /**
     * Get the BigDecimal value of the Document if it is a bigDecimal.
     *
     * <p>If the value is a number of a different type, the value is cast, which can result in a loss of precision.
     *
     * @return the BigDecimal value.
     * @throws SdkSerdeException if the Document is not a number.
     */
    default BigDecimal asBigDecimal() {
        throw new SdkSerdeException("Expected a bigDecimal document, but found " + type());
    }

    /**
     * Get the value of the builder as a Number.
     *
     * @return the Number value.
     * @throws SdkSerdeException if the Document is not a numeric type.
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
            default -> throw new SdkSerdeException("Expected a number document, but found " + type());
        };
    }

    /**
     * Get the string value of the Document if it is a string.
     *
     * <p>If the document is a blob, implementations should attempt to UTF-8 decode the blob value into a string.
     *
     * @return the string value.
     * @throws SdkSerdeException if the Document is not a string.
     */
    default String asString() {
        if (type() == ShapeType.BLOB) {
            return new String(asBlob(), StandardCharsets.UTF_8);
        }
        throw new SdkSerdeException("Expected a string document, but found " + type());
    }

    /**
     * Get the Document as a blob if the Document is a blob.
     *
     * <p>If the document is a string, implementations should return the UTF-8 bytes of the string.
     *
     * @return the bytes of the blob.
     * @throws SdkSerdeException if the Document is not a blob.
     */
    default byte[] asBlob() {
        if (type() == ShapeType.STRING) {
            return asString().getBytes(StandardCharsets.UTF_8);
        }
        throw new SdkSerdeException("Expected a blob document, but found " + type());
    }

    /**
     * Get the Document as an Instant if the Document is a timestamp.
     *
     * @return the Instant value of the timestamp.
     * @throws SdkSerdeException if the Document is not a timestamp.
     */
    default Instant asTimestamp() {
        throw new SdkSerdeException("Expected a timestamp document, but found " + type());
    }

    /**
     * Get the list contents of the Document if it is a list.
     *
     * @return the list contents.
     * @throws SdkSerdeException if the Document is not a list.
     */
    default List<Document> asList() {
        throw new SdkSerdeException("Expected a list document, but found " + type());
    }

    /**
     * Get the map contents of the Document if it is a map, a structure, or union.
     *
     * <p>If the Document is a map, the map entries are returned. If the document is a structure or union, the
     * members of the structure or union are returned as a Map.
     *
     * @return the map contents.
     * @throws SdkSerdeException if the Document is not a map, structure, or union.
     */
    default Map<Document, Document> asMap() {
        throw new SdkSerdeException("Expected a map document, but found " + type());
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
        throw new SdkSerdeException("Expected a map, structure, or union document, but found " + type());
    }

    /**
     * Attempt to deserialize the Document into a builder.
     *
     * @param builder Builder to populate from the Document.
     * @param <T> Shape type to build.
     */
    default <T extends SerializableShape> void deserializeInto(SdkShapeBuilder<T> builder) {
        builder.deserialize(new DocumentDeserializer(this));
    }

    /**
     * Attempt to deserialize the Document into a builder and create the shape.
     *
     * @param builder Builder to populate from the Document.
     * @return the built and error-corrected shape.
     * @param <T> Shape type to build.
     */
    default <T extends SerializableShape> T asShape(SdkShapeBuilder<T> builder) {
        deserializeInto(builder);
        return builder.errorCorrection().build();
    }

    /**
     * Create a byte Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(byte value) {
        return new Documents.ByteDocument(value);
    }

    /**
     * Create a short Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(short value) {
        return new Documents.ShortDocument(value);
    }

    /**
     * Create an integer Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(int value) {
        return new Documents.IntegerDocument(value);
    }

    /**
     * Create a long Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(long value) {
        return new Documents.LongDocument(value);
    }

    /**
     * Create a float Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(float value) {
        return new Documents.FloatDocument(value);
    }

    /**
     * Create a double Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(double value) {
        return new Documents.DoubleDocument(value);
    }

    /**
     * Create a bigInteger Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(BigInteger value) {
        return new Documents.BigIntegerDocument(value);
    }

    /**
     * Create a bigDecimal Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(BigDecimal value) {
        return new Documents.BigDecimalDocument(value);
    }

    /**
     * Create a boolean Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(boolean value) {
        return new Documents.BooleanDocument(value);
    }

    /**
     * Create a string Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(String value) {
        return new Documents.StringDocument(value);
    }

    /**
     * Create a blob Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(byte[] value) {
        return new Documents.BlobDocument(value);
    }

    /**
     * Create a timestamp Document.
     *
     * @param value Value to wrap.
     * @return the Document type.
     */
    static Document of(Instant value) {
        return new Documents.TimestampDocument(value);
    }

    /**
     * Create a list Document.
     *
     * @param values Values to wrap.
     * @return the Document type.
     */
    static Document of(List<Document> values) {
        return new Documents.ListDocument(values);
    }

    /**
     * Create a Map Document.
     *
     * @param members Members to wrap.
     * @return the Document type.
     */
    static Document ofMap(Map<Document, Document> members) {
        return new Documents.MapDocument(members);
    }

    /**
     * Create an untyped structure or union Document type.
     *
     * @param members Map of member name to document values.
     * @return the Document type.
     */
    static Document ofStruct(Map<String, Document> members) {
        return new Documents.StructureDocument(members);
    }

    /**
     * Create a typed Document from a structure or union shape.
     *
     * <p>The created Document can be serialized and deserialized by codec exactly as if the underlying shape is
     * serialized or deserialized.
     *
     * @param shape Shape to turn into a Document. The given value must emit a structure or union shape.
     * @return the Document type.
     * @throws SdkSerdeException if the shape is not a structure or union.
     */
    static Document ofStruct(SerializableShape shape) {
        return TypedDocument.of(shape);
    }

    /**
     * Create a Document from a shape.
     *
     * <p>This method can be used to normalize document types across codecs and typed documents, for example when
     * you need independent document type equality comparisons.
     *
     * @param shape Shape to turn into a Document.
     * @return the Document type.
     */
    static Document ofValue(SerializableShape shape) {
        var parser = new DocumentParser();
        shape.serialize(parser);
        return parser.getResult();
    }
}
