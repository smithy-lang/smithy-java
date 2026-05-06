# Codecs

> **Last updated:** April 29, 2026

Codecs are the abstraction Smithy-Java uses to read and write bytes from and to structures defined in the model. The
system is schema-driven, every serialization and deserialization call receives a `Schema` parameter that tells the
codec how to handle the value (field names, timestamp formats, XML attributes, etc.). This design enables
protocol-agnostic types: the same generated structure can be serialized to JSON, CBOR, or XML without any
format-specific code in the type itself.

**Source:** [`codecs/`](https://github.com/smithy-lang/smithy-java/tree/main/codecs)

## Architecture Overview

```
core/serde/           # Abstract interfaces: Codec, ShapeSerializer, ShapeDeserializer
codecs/
├── json-codec/       # JSON (RFC 8259), most feature-rich, two provider implementations
├── cbor-codec/       # CBOR binary format (RFC 7049), used by RPC v2
└── xml-codec/        # XML with Smithy XML trait support, used for legacy AWS protocols
```

## Core Interfaces

### Codec

[`Codec`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/serde/Codec.java)
is the top-level abstraction. It creates serializers and deserializers and provides convenience methods:

```java
public interface Codec extends AutoCloseable {
    ShapeSerializer createSerializer(OutputStream sink);
    ShapeDeserializer createDeserializer(ByteBuffer source);

    // Convenience: serialize shape to bytes
    default ByteBuffer serialize(SerializableShape shape);

    // Convenience: deserialize bytes to shape
    default <T extends SerializableShape> T deserializeShape(byte[] source, ShapeBuilder<T> builder);
}
```

The default `deserializeShape()` follows a three-step pattern:
`builder.deserialize(createDeserializer(source)).errorCorrection().build()`.

### ShapeSerializer

[`ShapeSerializer`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/serde/ShapeSerializer.java)
is the **push-based** serialization interface. Every write method takes a `Schema` parameter:

```java
void writeStruct(Schema schema, SerializableStruct struct);
void writeBoolean(Schema schema, boolean value);
void writeString(Schema schema, String value);
void writeInteger(Schema schema, int value);
void writeTimestamp(Schema schema, Instant value);
void writeBlob(Schema schema, ByteBuffer value);
void writeDocument(Schema schema, Document value);
void writeNull(Schema schema);
// ... and all other Smithy types
```

**Lists and maps use a callback pattern**, the serializer controls framing (brackets, tags) around the callback:

```java
<T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer);
<T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer);
```

This design lets the codec write `[` before and `]` after for JSON, or CBOR array headers, without the caller needing to
know the format.

### ShapeDeserializer

[`ShapeDeserializer`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/serde/ShapeDeserializer.java)
is the **pull-based** deserialization interface:

```java
boolean readBoolean(Schema schema);
String readString(Schema schema);
int readInteger(Schema schema);
Instant readTimestamp(Schema schema);
Document readDocument();

<T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer);
<T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer);
<T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer);
```

Struct deserialization uses a callback that receives each member:

```java
interface StructMemberConsumer<T> {
    void accept(T state, Schema memberSchema, ShapeDeserializer memberDeserializer);
    default void unknownMember(T state, String memberName) {}
}
```

The deserializer iterates over fields, resolves each field name to a `Schema` member, and calls the consumer with the
member schema and a deserializer positioned at the value.

### MapSerializer

```java
public interface MapSerializer {
    <T> void writeEntry(Schema keySchema, String key, T state,
                        BiConsumer<T, ShapeSerializer> valueSerializer);
}
```

### Supporting Classes

- **`InterceptingSerializer`** — Abstract class with `before(Schema)` and `after(Schema)` hooks. `before()` returns the delegate serializer. Used extensively in XML for element wrapping and in CBOR for struct member name writing.
- **`SpecificShapeSerializer`** / **`SpecificShapeDeserializer`** — Base classes that throw for all methods. Subclasses override only what they support.
- **`ListSerializer`** — Decorator that calls `beforeEachValue(position)` before delegating each write.
- **`NullSerializer`** — Singleton that silently discards all writes.
- **`TypeRegistry`** — Maps shape IDs to builders for polymorphic deserialization (error types, union variants).
- **`TimestampFormatter`** — Handles epoch-seconds, date-time (ISO 8601), and HTTP-date formats.

## Schema Interaction

Codecs use schema metadata extensively:

- `schema.type()` — Determines the Smithy shape type
- `schema.memberName()` — The field name to write/read
- `schema.memberIndex()` — Positional index for optimized field name lookup
- `schema.members()` — Children of struct/union schemas
- `schema.listMember()` / `schema.mapKeyMember()` / `schema.mapValueMember()` — Collection member schemas
- `schema.getTrait(TraitKey)` — Trait lookup (e.g., `@jsonName`, `@timestampFormat`, `@xmlAttribute`)
- `schema.getExtension(key)` — Lazily-computed, cached format-specific data

### Schema Extension System

Codecs pre-compute format-specific data on Schema objects via
[`SchemaExtensionProvider`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/schema/SchemaExtensionProvider.java)
SPI:

```java
public interface SchemaExtensionProvider<T> {
    SchemaExtensionKey<T> key();
    T provide(Schema schema);  // called lazily, cached on the Schema
}
```

This is how the JSON codec pre-computes UTF-8 byte arrays for field names, the XML codec pre-computes element/attribute
mappings, and the CBOR codec pre-computes canonicalized member name bytes.

## JSON Codec

**Source:** [`codecs/json-codec/`](https://github.com/smithy-lang/smithy-java/tree/main/codecs/json-codec)

### JsonCodec

`JsonCodec` implements `Codec` and delegates to a pluggable `JsonSerdeProvider`. Configuration:

```java
JsonCodec codec = JsonCodec.builder()
    .useJsonName(true)              // honor @jsonName trait
    .useTimestampFormat(true)       // honor @timestampFormat trait
    .defaultTimestampFormat(TimestampFormatter.EPOCH_SECONDS)
    .prettyPrint(false)
    .build();
```

### Provider Architecture

Two implementations, selected via `ServiceLoader` with priority ordering:

1. **`SmithyJsonSerdeProvider`** (priority 5, name "smithy") — High-performance native implementation. The default.
2. **`JacksonJsonSerdeProvider`** (priority 0, name "jackson") — Jackson-based fallback.

Override via system property: `-Dsmithy-java.json-provider=smithy|jackson`.

### SmithyJsonSerializer (Native)

The native JSON serializer writes directly to a `byte[]` buffer with no intermediate String/char operations. Key
optimizations:

- **Object pooling** — Striped `AtomicReferenceArray` pool (`processors * 4` slots, power-of-2). Uses
  `compareAndExchangeAcquire/Release` for lock-free acquire/release. Skips pooling for virtual threads.
- **Pre-computed field names** — Resolves `byte[][]` field name tables from `SmithyJsonSchemaExtensions` once per
  `writeStruct`, then uses `System.arraycopy` for each member. The pre-computed bytes include quotes and colon (e.g.,
  `"fieldName":`).
- **Fused capacity checks** — For struct members, computes `nameBytes.length + 1 + maxValueBytes` in a single
  `ensureCapacity` call.
- **Custom number formatting** — Uses `Schubfach` algorithm for double/float-to-decimal conversion (avoids
  `Double.toString()`).
- **Nesting via `boolean[] needsComma`** — Tracks comma insertion per depth level (max 64).

Serialization flow for a struct:
1. Write `{`, increment depth
2. Resolve field name table from schema extension
3. Call `struct.serializeMembers(structSerializer)`
4. For each member, the inner `StructSerializer` resolves pre-computed field name bytes by `memberIndex`, writes comma if needed, writes field name bytes, then delegates value writing
5. Write `}`

### SmithyJsonDeserializer (Native)

Operates directly on `byte[]` with `int pos/end` cursors. Key optimizations:

- **Speculative field matching** — In `readStruct`, tries to match the next field name against the expected next member
  (by schema definition order) using `Arrays.equals` on raw bytes. If it matches, skips the hash lookup entirely. Falls
  back to `SmithyMemberLookup.lookup()` (hash-based) on miss.
- **Localized hot fields** — Copies `this.buf/end/pos` to local variables before struct/list/map loops to help JIT
  register allocation.
- **Fast timestamp parsing** — For epoch-seconds, tries `parseLong` first (most timestamps are whole numbers), then
  handles fractional nanoseconds directly from bytes. For ISO-8601 and HTTP-date, parses directly from bytes bypassing
  `DateTimeFormatter`.
- **Mutable result fields** — `parsedLong`, `parsedDouble`, `parsedString` avoid allocations.

### SmithyJsonSchemaExtensions

Pre-computes per-schema:
- UTF-8 byte arrays for field names (including quotes and colon)
- `SmithyMemberLookup` hash tables for O(1) field resolution during deserialization
- Indexed field name tables by `memberIndex` for O(1) lookup during serialization

## CBOR Codec

**Source:** [`codecs/cbor-codec/`](https://github.com/smithy-lang/smithy-java/tree/main/codecs/cbor-codec)

### Rpcv2CborCodec

Simpler than JSON, no `@jsonName` or `@timestampFormat` trait handling. Delegates to `CborSerdeProvider` (SPI-loaded).

### CborSerializer

Writes CBOR binary format to a `Sink` abstraction (three implementations: `OutputStreamSink`, `ResizingSink`,
`NullSink`).

Key characteristics:
- **Indefinite-length maps for structs** — Uses `0xBF` (indefinite map start) with `0xFF` (break byte), since member
  count isn't known upfront.
- **Definite-length arrays for lists** — Uses definite length when size is known.
- **Compact integer encoding** — Selects 1/2/4/8 byte encoding based on value magnitude.
- **Timestamps as tagged epoch** — CBOR tag 1 wrapping a double of epoch milliseconds / 1000.
- **BigInteger** — CBOR tags 2 (positive) and 3 (negative) with byte string payload.
- **BigDecimal** — CBOR tag 4 with `[exponent, mantissa]` array.

Inner `CborStructSerializer` extends `InterceptingSerializer`, `before(Schema)` writes the member name as a CBOR text
string, returns the parent serializer for value writing.

### CborDeserializer

Uses `CborParser` (token-based parser) operating on `byte[]` payload.

Key characteristics:
- **Canonicalizer for struct member lookup** — `Canonicalizer` class pre-computes UTF-8 byte arrays for all members,
  grouped by length. Resolution compares raw bytes at the parser position without String allocation. Cached in a static
  `ConcurrentHashMap<Schema, Canonicalizer>`.
- **Half-precision float support** — Decodes IEEE 754 half-precision floats.
- **Null skipping** — `readStruct` skips explicit null values without dispatching events.
- **Container size** — `containerSize()` delegates to `parser.collectionSize()` for pre-allocation hints.

### Sink Interface

Sealed interface with three implementations:
- `OutputStreamSink` — Wraps `OutputStream`
- `ResizingSink` — Growable `byte[]` buffer with `finish()` returning `ByteBuffer`
- `NullSink` — Discards all writes (for size calculation)

## XML Codec

**Source:** [`codecs/xml-codec/`](https://github.com/smithy-lang/smithy-java/tree/main/codecs/xml-codec)

The XML codec is the most complex due to XML's structural differences from the Smithy data model. It uses
`javax.xml.stream` (StAX) for both reading and writing, with DTD and external entity support disabled for security.

### XmlCodec

```java
XmlCodec codec = XmlCodec.builder()
    .wrapperElements(List.of("OperationNameResponse", "OperationNameResult"))
    .build();
```

The `wrapperElements` configuration is used for AWS Query protocol, where responses are wrapped in extra elements.

### XmlSerializer

Extends `InterceptingSerializer` and uses a hierarchy of 6+ inner serializer classes:

1. **`XmlSerializer`** (top-level) — Writes root element with `@xmlName` trait and namespace.
2. **`StructMemberSerializer`** — Routes members: attributes → skip, flattened → `ValueSerializer`, normal → `NonFlattenedMemberSerializer`.
3. **`StructAttributeSerializer`** — Routes: attributes → `AttributeSerializer`, non-attributes → skip.
4. **`NonFlattenedMemberSerializer`** — Wraps each member in `<memberName>...</memberName>`.
5. **`ValueSerializer`** — Writes actual values. For structs, first serializes attributes then members.
6. **`AttributeSerializer`** — Writes values as XML attributes via `writer.writeAttribute()`.

Struct serialization flow:
1. Write `<XmlName xmlns="...">`
2. If struct has attributes, call `struct.serializeMembers(structAttributeSerializer)` first
3. Then call `struct.serializeMembers(structMemberSerializer)` for elements
4. Write `</XmlName>`

Trait handling:
- `@xmlName` — Overrides element/attribute names
- `@xmlAttribute` — Serializes as XML attribute instead of element
- `@xmlFlattened` — Skips wrapper element for lists/maps
- `@xmlNamespace` — Adds namespace declarations

### XmlDeserializer

Uses `XmlReader` (wraps StAX `XMLStreamReader` or buffered events). Two-layer design:

- **`XmlDeserializer`** (outer) — Handles top-level element entry/exit, validates root element name.
- **`InnerDeserializer`** (inner) — Handles actual value parsing from XML text content.

Key features:
- **Wrapper element skipping** — For AWS Query protocol, skips `<OperationNameResponse>` and `<OperationNameResult>`
  wrappers.
- **Flattened member buffering** — For interspersed flattened lists (e.g., S3's `ListObjectVersions`), buffers XML
  events per member, then replays them via `XmlReader.BufferedReader` after all non-flattened members are read.
- **Attribute deserialization** — `AttributeDeserializer` reads attribute values as strings and parses to target types.

### XmlInfo

Caches pre-computed XML metadata per Schema in `ConcurrentHashMap`s:

- **`StructInfo`** — `xmlName`, `xmlNamespace`, attribute/element member maps, `hasFlattened` flag
- **`ListMemberInfo`** — `xmlName`, `memberName` (default "member"), `flattened` flag
- **`MapMemberInfo`** — `xmlName`, `entryName` (default "entry"), `keyName`, `valueName`, `flattened` flag

## Serialization/Deserialization Flow

### Serialization (shape → bytes)

```
codec.serialize(shape)
  → codec.createSerializer(outputStream)
  → shape.serialize(serializer)
  → serializer.writeStruct(schema, struct)
  → struct.serializeMembers(innerSerializer)
  → innerSerializer.writeString(memberSchema, value)   // for each member
  → format-specific byte writing
```

### Deserialization (bytes → shape)

```
codec.deserializeShape(bytes, builder)
  → codec.createDeserializer(bytes)
  → builder.deserialize(deserializer)
  → deserializer.readStruct(schema, builder, consumer)
  → for each field: resolve member schema, call consumer.accept(builder, memberSchema, deserializer)
  → consumer reads value: deserializer.readString(memberSchema)
  → builder.errorCorrection().build()
```

## Key Design Patterns

1. **Push-based serialization, pull-based deserialization** — Serializers receive data pushed by shapes. Deserializers
   pull data guided by schemas.

2. **Schema-driven** — Every read/write takes a `Schema` parameter. Codecs use schema metadata (traits, type, member
   index) to determine format-specific behavior.

3. **Callback-based containers** — Lists and maps use `BiConsumer` callbacks rather than returning sub-serializers. This
   lets the codec control framing around the callback.

4. **Extension pre-computation** — Format-specific data (field name bytes, member lookups, timestamp formatters) is
   computed lazily on first access and cached on Schema objects via `SchemaExtensionProvider` SPI.

5. **Provider SPI** — Both JSON and CBOR use `ServiceLoader`-based provider selection with priority ordering, allowing
   pluggable implementations.

6. **InterceptingSerializer pattern** — Used in XML and CBOR for wrapping writes with before/after hooks (element
   open/close, field name writing).

7. **Speculative optimization** — The native JSON deserializer speculatively matches field names in schema definition
   order, avoiding hash lookups on the common path.

8. **Object pooling** — The native JSON serializer uses a lock-free striped pool for buffer reuse, with virtual-thread
   awareness.
