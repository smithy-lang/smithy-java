# Documents

> **Last updated:** April 29, 2026

Documents are a first-class abstraction in the Smithy data model, a `document` type represents protocol-agnostic open
content that can hold any Smithy value. In Smithy-Java, the `Document` interface is the Java equivalent: it can
represent any Smithy shape (string, number, list, map, structure, etc.) without requiring a generated type. Documents
are the foundation of the dynamic client and are used throughout the framework for untyped data handling.

**Source:**
  [`core/src/main/java/software/amazon/smithy/java/core/serde/document/`](https://github.com/smithy-lang/smithy-java/tree/main/core/src/main/java/software/amazon/smithy/java/core/serde/document)

## The Document Interface

[`Document`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/serde/document/Document.java)
is a Java interface that extends `SerializableShape`. It represents untyped data from the Smithy data model.

### Core Design Principles

1. **Protocol-agnostic** — Documents represent the Smithy data model, not any wire format. Protocol codecs smooth over
   incompatibilities (e.g., base64-encoded blobs in JSON are transparently decoded by `asBlob()`).

2. **Dual serialization contract** — `serialize(ShapeSerializer)` always calls `serializer.writeDocument(schema, this)`,
   while `serializeContents(ShapeSerializer)` emits the *inner* data model (string, number, struct, etc.). This
   two-level design lets serializers choose whether to treat the value as an opaque document or drill into its contents.

3. **Typed documents** — Documents can wrap a `SerializableShape` via `Document.of(SerializableShape)`, preserving the
   full schema so codecs can serialize/deserialize them exactly as if the original shape were used directly.

### Key Methods

```java
// Type identification
ShapeType type();

// The two-level serialization contract
void serialize(ShapeSerializer encoder);          // calls writeDocument(schema, this)
void serializeContents(ShapeSerializer serializer); // emits inner data model

// Scalar accessors (throw SerializationException if wrong type)
boolean asBoolean();
int asInteger();
long asLong();
String asString();
ByteBuffer asBlob();
Instant asTimestamp();
// ... and byte, short, float, double, BigInteger, BigDecimal

// Collection accessors
List<Document> asList();
Map<String, Document> asStringMap();

// Member access (for map/struct/union documents)
Document getMember(String name);
Collection<String> getMemberNames();
int size();

// Recursive unwrap to standard Java types
Object asObject();

// Discriminator support (for typed documents)
ShapeId discriminator();

// Convert to a generated shape
<T extends SerializableShape> T asShape(ShapeBuilder<T> builder);
```

## Document Implementations

All concrete implementations are **package-private Java records** inside the `Documents` class:

| Smithy Type | Record | Key Field |
|---|---|---|
| boolean | `BooleanDocument` | `boolean value` |
| byte | `ByteDocument` | `byte value` |
| short | `ShortDocument` | `short value` |
| integer | `IntegerDocument` | `int value` |
| long | `LongDocument` | `long value` |
| float | `FloatDocument` | `float value` |
| double | `DoubleDocument` | `double value` |
| bigInteger/bigDecimal | `NumberDocument` | `Number value` |
| string | `StringDocument` | `String value` |
| blob | `BlobDocument` | `ByteBuffer value` |
| timestamp | `TimestampDocument` | `Instant value` |
| list | `ListDocument` | `List<Document> values` |
| map | `StringMapDocument` | `Map<String, Document> members` |
| structure/union | `StructureDocument` | `Map<String, Document> members` |
| structure (lazy) | `LazyStructure` | `SerializableStruct struct` |
| streaming blob | `DataStreamDocument` | `DataStream stream` |
| streaming union | `EventStreamDocument` | `EventStream stream` |

### Numeric Types

Numeric types use primitive records (`ByteDocument`, `IntegerDocument`, etc.) to avoid autoboxing. All numeric documents
support cross-type casting, `IntegerDocument.asLong()` returns `(long) value`, following JLS widening/narrowing
rules. `NumberDocument` is used only for `BigInteger` and `BigDecimal` (already objects).

### StructureDocument

`StructureDocument` implements both `Document` and `SerializableStruct`, providing `serializeMembers()` and
`getMemberValue()`. It stores members as `Map<String, Document>`.

### LazyStructure — The Key Optimization

When `Document.of(SerializableShape)` is called on a structure, the result is a `LazyStructure` that **defers member
parsing** until `getMember()`, `asStringMap()`, or `getMemberNames()` is called. If the document is only serialized
(never inspected), the original struct's `serialize()` is used directly, no intermediate document tree is created.

```java
// LazyStructure wraps the original struct
class LazyStructure implements Document {
    private final SerializableStruct struct;
    private volatile StructureDocument materialized; // lazy

    void serializeContents(ShapeSerializer serializer) {
        struct.serialize(serializer); // direct delegation, no materialization
    }

    Document getMember(String name) {
        return materialize().getMember(name); // materializes on first access
    }
}
```

Materialization uses `DocumentParser.StructureParser` to parse the struct's members into a `StructureDocument`. The `volatile` field ensures thread-safe lazy initialization.

## Creating Documents

### Static Factory Methods

**Scalar factories:**
```java
Document.of(true)                    // → BooleanDocument
Document.of(42)                      // → IntegerDocument
Document.of("hello")                 // → StringDocument
Document.of(Instant.now())           // → TimestampDocument
Document.of(ByteBuffer.wrap(bytes))  // → BlobDocument
```

**Collection factories:**
```java
Document.of(List.of(Document.of(1), Document.of(2)))  // → ListDocument
Document.of(Map.of("key", Document.of("value")))       // → StringMapDocument
```

**Streaming factories:**
```java
Document.of(schema, dataStream)    // → DataStreamDocument
Document.of(schema, eventStream)   // → EventStreamDocument
```

### Document.ofObject(Object) — Universal Factory

Accepts any standard Java type and recursively converts:

```java
Document.ofObject("hello")                    // → StringDocument
Document.ofObject(42)                         // → IntegerDocument
Document.ofObject(List.of("a", "b"))          // → ListDocument of StringDocuments
Document.ofObject(Map.of("k", "v"))           // → StringMapDocument
Document.ofObject(mySerializableStruct)       // → typed document (LazyStructure)
```

This is the primary entry point for the dynamic client's `call(String, Map)` method.

### Document.of(SerializableShape) — Typed Documents

Captures the full state of any `SerializableShape` into a document tree:

```java
Document doc = Document.of(myStruct);
// For structures: returns LazyStructure (defers member parsing)
// For other shapes: uses DocumentParser to capture the value
```

## The Serialize/SerializeContents Two-Level Pattern

This is the most important design pattern in the document system.

```
serialize(serializer)
  → serializer.writeDocument(schema, this)    // "I am a document"
      → codec decides how to handle it
      → typically calls document.serializeContents(serializer)
          → serializer.writeString(schema, value)  // "My content is a string"
```

**Why two levels?**

- `serialize()` wraps the value as a document (`writeDocument`). This tells the codec "this is an opaque document value."
- `serializeContents()` emits the raw data model. This tells the codec "here's what's inside."
- Codecs receive documents via `writeDocument` and can choose to inline the contents via `serializeContents()`.
- This prevents infinite recursion while allowing codecs full access to document internals.

For example, the JSON codec's `writeDocument` for structures writes a `__type` discriminator field before the contents,
while for scalars it just inlines the value.

## DocumentParser — Shapes to Documents

[`DocumentParser`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/serde/document/DocumentParser.java)
implements `ShapeSerializer` and converts any serialized Smithy data model into `Document` objects. It's the inverse of
`serializeContents`.

```java
// Usage (internal):
var parser = new DocumentParser();
shape.serialize(parser);
Document result = parser.getResult();
```

Key behavior:
- Each `write*` method creates the corresponding document record and stores it in `result`
- `writeStruct` for STRUCTURE types creates a `LazyStructure`; for unions, it eagerly parses members
- `writeDocument` calls `value.serializeContents(this)` — unwrapping documents recursively
- `writeList` / `writeMap` collect elements via inner serializers

`DocumentParser.StructureParser` extends `InterceptingSerializer`:
- `before(Schema)` returns the inner `DocumentParser`
- `after(Schema)` stores the parsed result keyed by `schema.memberName()`
- Produces a `Map<String, Document>` of member name → document

## DocumentDeserializer — Documents to Shapes

[`DocumentDeserializer`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/serde/document/DocumentDeserializer.java)
implements `ShapeDeserializer` and wraps a `Document`, allowing any document to be deserialized into a generated shape:

```java
// Convert a document to a generated type:
MyStruct struct = document.asShape(MyStruct.builder());
```

Internally, `asShape()` calls `builder.deserialize(new DocumentDeserializer(this))`.

The deserializer delegates to the document's type accessors:
- `readString(schema)` → `value.asString()`
- `readInteger(schema)` → `value.asInteger()`
- `readStruct(schema, state, consumer)` → iterates `value.getMemberNames()`, resolves member schemas, calls consumer

**Extensibility**: `DocumentDeserializer` is designed to be extended by codecs. The `deserializer(Document)` factory
  method is `protected` so subclasses can return codec-specific deserializers.

### Discriminator Parsing

```java
DocumentDeserializer.parseDiscriminator(String text, String defaultNamespace)
```

Parses shape IDs from discriminator strings (e.g., `__type` in JSON). Handles both fully-qualified
(`com.example#MyError`) and short-form (`MyError`) discriminators.

## Equality and Comparison

### Document.equals(Document left, Document right, int options)

Compares documents by type and value, ignoring schemas. Supports `DocumentEqualsFlags.NUMBER_PROMOTION` for JLS 5.1.2
widening comparison. Recursively compares lists element-by-element and maps entry-by-entry. `BigDecimal` comparison uses
`stripTrailingZeros()` for value equality.

### Document.compare(Document left, Document right)

Supports numeric and string types only. Uses `DocumentUtils.compareWithPromotion()` for cross-type numeric comparison —
promotes to `BigDecimal` when either operand is `BigInteger`/`BigDecimal`, to `double` when either is `float`/`double`,
otherwise uses `long`.

## Relationship to the Dynamic Client

The dynamic client uses documents as its input/output type. When a user calls:

```java
Document result = client.call("GetItem", Map.of("id", "123"));
```

The flow is:
1. `Map.of("id", "123")` is converted to a `Document` via `Document.ofObject()`
2. The document is wrapped into a `StructDocument` (from `dynamic-schemas`) via `StructDocument.of(inputSchema,
   document, serviceId)`, this recursively wraps every nested value with the correct schema
3. The `StructDocument` implements `SerializableStruct`, so the protocol codec can serialize it with full schema
   information
4. The response is deserialized into a `StructDocument` via `SchemaGuidedDocumentBuilder`
5. The user receives it as a `Document` and accesses members via `result.getMember("name").asString()`

See the [Dynamic Client](dynamic-client.md) document for details on `StructDocument` and
`ContentDocument`.

## Key Classes Summary

| Class | Role |
|---|---|
| `Document` | Core interface, untyped Smithy data model value |
| `Documents` | Package-private record implementations for all types |
| `Documents.LazyStructure` | Lazy typed structure wrapping a `SerializableStruct` |
| `DocumentParser` | `ShapeSerializer` that converts shapes → documents |
| `DocumentDeserializer` | `ShapeDeserializer` that reads from documents (extensible) |
| `DocumentUtils` | Number serialization, comparison, member value extraction |
| `DocumentEqualsFlags` | Bitfield flags for equality options |
| `DataStreamDocument` | Document wrapping a `DataStream` |
| `EventStreamDocument` | Document wrapping an `EventStream` |
| `DiscriminatorException` | Exception for missing/invalid discriminators |
