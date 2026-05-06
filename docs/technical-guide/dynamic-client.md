# Dynamic Client

> **Last updated:** April 29, 2026

The dynamic client is an innovation in Smithy-Java that allows creating Smithy clients at runtime from a Smithy model,
without code generation. Instead of generated POJOs for inputs and outputs, it uses `Document` objects guided by
`Schema` objects derived from the model. This makes it possible to call any Smithy service as long as you have its model
— useful for tooling, testing, proxying, and scenarios where codegen isn't practical.

**Source:**
- [`client/dynamic-client/`](https://github.com/smithy-lang/smithy-java/tree/main/client/dynamic-client) — The
  `DynamicClient` class and supporting plugins
- [`dynamic-schemas/`](https://github.com/smithy-lang/smithy-java/tree/main/dynamic-schemas) — Schema conversion and
  schema-aware document types

## DynamicClient

[`DynamicClient`](https://github.com/smithy-lang/smithy-java/blob/main/client/dynamic-client/src/main/java/software/amazon/smithy/java/dynamicclient/DynamicClient.java)
extends `Client` — it's a full Smithy client inheriting the standard client pipeline (interceptors, auth, transport,
protocol, endpoint resolution).

### Builder

```java
DynamicClient client = DynamicClient.builder()
    .model(model)                          // Smithy Model (required)
    .serviceId(ShapeId.from("com.example#MyService"))  // optional if model has exactly one service
    .protocol(new Rpcv2CborProtocol())     // protocol
    .transport(new JavaHttpClientTransport()) // transport
    .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
    .build();
```

The builder implements two setting interfaces:
- **`ModelSetting<Builder>`** — provides `model(Model)`, stores in `Context.Key<Model> MODEL`
- **`ServiceIdSetting<Builder>`** — provides `serviceId(ToShapeId)`, stores in `Context.Key<ShapeId> SERVICE_ID`

### Build Logic

1. Retrieves the `Model` from context (required)
2. Resolves the `ServiceShape`:
   - If `serviceId` is set, looks it up in the model
   - If not set, auto-detects if the model contains exactly one service (throws if 0 or >1)
3. Creates a `SchemaConverter(model)` and converts the service shape to a `Schema`
4. Creates an `ApiService` wrapping the service schema
5. Runs `DetectProtocolPlugin` to auto-detect protocol from model traits if not explicitly set
6. Builds a `TypeRegistry` for service-level errors

### Public API — call() Methods

```java
// No input (empty document)
Document result = client.call("GetItems");

// Map input (converted via Document.ofObject)
Document result = client.call("GetItem", Map.of("id", "123"));

// Document input
Document result = client.call("GetItem", Document.of(Map.of("id", Document.of("123"))));

// With request override config
Document result = client.call("GetItem", input, overrideConfig);
```

All `call()` methods:
1. Resolve the `ApiOperation` by name (lazy, cached in `ConcurrentHashMap`)
2. Convert the input `Document` to a `StructDocument` via `StructDocument.of(inputSchema, input, serviceId)`
3. Delegate to the inherited `Client.call(inputStruct, apiOperation, overrideConfig)`

### Other Methods

```java
// Get the raw ApiOperation for advanced use
ApiOperation<StructDocument, StructDocument> op = client.getOperation("GetItem");

// Create a SerializableStruct from any shape in the model
SerializableStruct struct = client.createStruct(ShapeId.from("com.example#MyStruct"), document);
```

## SchemaConverter

[`SchemaConverter`](https://github.com/smithy-lang/smithy-java/blob/main/dynamic-schemas/src/main/java/software/amazon/smithy/java/dynamicschemas/SchemaConverter.java) converts Smithy `Shape` objects (from `software.amazon.smithy.model`) into runtime `Schema` objects (from `software.amazon.smithy.java.core.schema`).

```java
SchemaConverter converter = new SchemaConverter(model);
Schema schema = converter.getSchema(shape);
```

### Conversion Strategy

- **Scalar shapes** (string, boolean, integer, etc.) — Direct creation via `Schema.createXxx(shapeId, traits)`
- **Aggregate shapes** (structure, union, list, map) — Recursive builder pattern with cycle detection

### Recursive Schema Handling

The converter maintains:
- `Map<Shape, SchemaBuilder> recursiveBuilders` — Detects and handles recursive references
- `Set<Shape> building` — Tracks shapes currently being built (cycle detection)

When a cycle is detected (target shape is in `building`), it uses the deferred `SchemaBuilder` reference instead of a completed `Schema`, producing a `DeferredRootSchema` at build time.

### Caching

Results are cached in `ConcurrentMap<Shape, Schema> schemas`. The converter also provides a `SchemaIndex` via `getSchemaIndex()` that delegates to this cache.

### Document Builder Factory

```java
// Creates a ShapeBuilder that produces StructDocuments
ShapeBuilder<StructDocument> builder = SchemaConverter.createDocumentBuilder(schema, serviceId);
```

This is what protocols use to deserialize responses into `StructDocument` instances.

## StructDocument — The Bridge

[`StructDocument`](https://github.com/smithy-lang/smithy-java/blob/main/dynamic-schemas/src/main/java/software/amazon/smithy/java/dynamicschemas/StructDocument.java)
is the central type that makes the dynamic client work. It implements both `Document` AND `SerializableStruct`, allowing
it to stand in for a generated POJO anywhere in the client pipeline.

### Key Design Decision

> StructDocument intentionally breaks the normal Document invariant. Its `serialize()` calls `serializeContents()`
> (which calls `writeStruct`) instead of `writeDocument`. This is because StructDocument is meant to stand in for a
> modeled value, not be serialized as a document.

### Fields

```java
private final Schema schema;                    // The Smithy schema this document represents
private final ShapeId service;                  // Service ID for namespace resolution
private final Map<String, Document> members;    // Member name → Document value
```

### Deep Conversion — StructDocument.of()

```java
StructDocument input = StructDocument.of(inputSchema, document, serviceId);
```

This recursively walks the schema tree and the document tree in parallel, wrapping each value with the correct schema:

- **STRUCTURE** — Iterates schema members, looks up each in the delegate, recursively converts → `StructDocument`
- **UNION** — Same as structure, handles `@streaming` unions as event streams
- **MAP** — Wraps each value with the map's value member schema → `ContentDocument`
- **LIST/SET** — Wraps each element with the list's member schema → `ContentDocument`
- **Scalars** (BOOLEAN, STRING, INTEGER, etc.) — Wraps in `ContentDocument` with the correct schema
- **BLOB with @streaming** — Passes through as `Document.of(schema, delegate.asDataStream())`
- **DOCUMENT type** — Wraps as-is in `ContentDocument`

### Serialization

When the protocol serializes a `StructDocument`:
1. `serialize()` calls `serializeContents()` which calls `serializer.writeStruct(schema, this)`
2. `serializeMembers()` iterates member names, looks up the schema member, calls `value.serialize(serializer)` on each
3. The protocol sees correct shape IDs, traits (like `@jsonName`, `@timestampFormat`), and types for every value

### Shape ID Awareness (Discriminator)

```java
public ShapeId discriminator() {
    return schema.type() == ShapeType.STRUCTURE ? schema.id() : null;
}
```

For structures, returns the schema's shape ID. This is used by protocols for error discrimination (e.g., `__type` in AWS
JSON).

## ContentDocument

[`ContentDocument`](https://github.com/smithy-lang/smithy-java/blob/main/dynamic-schemas/src/main/java/software/amazon/smithy/java/dynamicschemas/ContentDocument.java) is a record that wraps another `Document` and overrides its schema:

```java
record ContentDocument(Document document, Schema schema) implements Document
```

Used for non-structure values (scalars, lists, maps) that need schema association. While `StructDocument` handles
structures/unions, `ContentDocument` handles everything else.

Key behavior:
- For `DOCUMENT` type: calls `serializer.writeDocument(schema, this)` (preserves document semantics)
- For all other types: calls the appropriate `serializer.writeXxx(schema, value)` with the overridden schema
- All `asXxx()` methods delegate to the wrapped document

## SchemaGuidedDocumentBuilder

[`SchemaGuidedDocumentBuilder`](https://github.com/smithy-lang/smithy-java/blob/main/dynamic-schemas/src/main/java/software/amazon/smithy/java/dynamicschemas/SchemaGuidedDocumentBuilder.java)
implements `ShapeBuilder<StructDocument>`. This is what protocols use to deserialize responses into `StructDocument`
instances.

### Deserialization

The `deserialize(ShapeDeserializer)` method handles every Smithy type:
- **Scalars** — Reads via `decoder.readXxx(schema)`, wraps in `ContentDocument`
- **LIST** — Uses `decoder.readList()` with a `SchemaList` (ArrayList subclass that captures the member schema)
- **MAP** — Uses `decoder.readStringMap()` with a `SchemaMap` (HashMap subclass that captures the schema)
- **STRUCTURE/UNION** — Uses `decoder.readStruct()` to build a `LinkedHashMap<String, Document>`, creates a `StructDocument`
- **Streaming BLOB** — Uses `decoder.readDataStream()`
- **Streaming UNION** — Uses `decoder.readEventStream()`

### build()

For unions, throws if no value was set. Returns `new StructDocument(target, map, service)`.

## DynamicOperation

[`DynamicOperation`](https://github.com/smithy-lang/smithy-java/blob/main/client/dynamic-client/src/main/java/software/amazon/smithy/java/dynamicclient/DynamicOperation.java)
implements `ApiOperation<StructDocument, StructDocument>`. This is the runtime representation of a Smithy operation
without codegen.

Key fields:
- `operationSchema`, `inputSchema`, `outputSchema` — `Schema` objects converted from the model
- `errorSchemas` — `List<Schema>` for operation-specific errors
- `typeRegistry` — Composed from operation errors + service errors, used for error deserialization
- `effectiveAuthSchemes` — `List<ShapeId>` from `ServiceIndex.getEffectiveAuthSchemes()`

`inputBuilder()` / `outputBuilder()` return `SchemaGuidedDocumentBuilder` instances.

## DocumentException

[`DocumentException`](https://github.com/smithy-lang/smithy-java/blob/main/client/dynamic-client/src/main/java/software/amazon/smithy/java/dynamicclient/DocumentException.java)
extends `ModeledException`. Wraps a `StructDocument` representing the error contents.

- `getContents()` returns the error as a `Document` for inspection
- `createMessage()` attempts to extract a `message` or `Message` member from the document

Its inner `SchemaGuidedExceptionBuilder` is registered in the `TypeRegistry` for error deserialization.

## Plugins

### DetectProtocolPlugin

Runs at `FIRST` phase. Uses `ServiceLoader` to discover `ClientProtocolFactory` implementations on the classpath. Reads
protocol traits from the service via `ServiceIndex.of(model).getProtocols(service)`. If a transport is already set, only
picks protocols whose `messageExchange()` matches the transport's.

### SimpleAuthDetectionPlugin

Runs at `DEFAULTS` phase. Auto-discovered via SPI. Uses `ServiceLoader` to discover `AuthSchemeFactory`
implementations. Reads effective auth schemes from `ServiceIndex.getEffectiveAuthSchemes()`. Only applies if no
`AuthSchemeResolver` is already configured.

## Request/Response Flow

### Sending a Request

1. User calls `client.call("GetItem", Map.of("id", "123"))`
2. `Map` is converted to `Document` via `Document.ofObject()`
3. `getApiOperation("GetItem")` lazily creates a `DynamicOperation` with schemas from `SchemaConverter`
4. Input `Document` is wrapped into a `StructDocument` via `StructDocument.of(inputSchema, document, serviceId)` —
   recursively wraps every nested value with the correct schema
5. The `StructDocument` (which implements `SerializableStruct`) is passed to the protocol layer
6. The protocol serializes it using `serialize()` / `serializeMembers()`, which emit proper schema-tagged values
7. The protocol sees correct shape IDs, traits, and types for every value

### Receiving a Response

1. The protocol receives the raw response
2. It calls `apiOperation.outputBuilder()` which returns a `SchemaGuidedDocumentBuilder`
3. The protocol calls `builder.deserialize(decoder)`
4. The builder reads each field using the schema to guide deserialization, wrapping values in `ContentDocument` or
   `StructDocument`
5. `builder.build()` returns a `StructDocument` that the user receives as a `Document`

### Error Handling

1. The protocol detects an error response
2. It looks up the error type in the `TypeRegistry` (composed from operation + service errors)
3. The registry returns a `DocumentException.SchemaGuidedExceptionBuilder`
4. The builder deserializes the error body into a `StructDocument`
5. A `DocumentException` is thrown, which the user can catch and inspect via `getContents()`

## Differences from Generated Clients

| Aspect | Generated Client | Dynamic Client |
|--------|-----------------|----------------|
| Types | Generated POJOs per shape | `Document` / `StructDocument` |
| Schema source | Static `Schema` constants in generated code | `SchemaConverter` converts from `Model` at runtime |
| Input construction | Builder pattern on generated types | `Document.ofObject(Map)` or `Document.of(...)` |
| Output access | Typed getters | `document.getMember("name").asString()` |
| Error types | Generated exception classes | `DocumentException` with `getContents()` |
| Protocol detection | Hardcoded in generated code | Auto-detected via `ServiceLoader` + model traits |
| Auth detection | Hardcoded in generated code | Auto-detected via `SimpleAuthDetectionPlugin` |

## File Layout

```
client/dynamic-client/src/main/java/software/amazon/smithy/java/dynamicclient/
├── DynamicClient.java              — Main client class
├── DynamicOperation.java           — Runtime operation representation
├── DocumentException.java          — Schema-aware exception + builder
├── plugins/
│   ├── DetectProtocolPlugin.java   — Auto-detect protocol from model
│   └── SimpleAuthDetectionPlugin.java — Auto-detect auth schemes
└── settings/
    ├── ModelSetting.java           — Mixin for Model config
    └── ServiceIdSetting.java       — Mixin for ServiceId config

dynamic-schemas/src/main/java/software/amazon/smithy/java/dynamicschemas/
├── SchemaConverter.java            — Shape → Schema conversion with recursion handling
├── StructDocument.java             — Document + SerializableStruct hybrid
├── ContentDocument.java            — Schema-wrapping document for non-struct values
└── SchemaGuidedDocumentBuilder.java — ShapeBuilder for deserializing into StructDocument
```

## Key Classes Summary

| Class | Module | Role |
|---|---|---|
| `DynamicClient` | dynamic-client | Main client class, extends `Client` |
| `DynamicOperation` | dynamic-client | `ApiOperation<StructDocument, StructDocument>` |
| `DocumentException` | dynamic-client | Error wrapper with `getContents()` |
| `DetectProtocolPlugin` | dynamic-client | Auto-detects protocol from model traits |
| `SimpleAuthDetectionPlugin` | dynamic-client | Auto-detects auth schemes |
| `ModelSetting` | dynamic-client | Builder mixin for `Model` |
| `ServiceIdSetting` | dynamic-client | Builder mixin for `ShapeId` |
| `SchemaConverter` | dynamic-schemas | Converts Smithy `Shape` → runtime `Schema` |
| `StructDocument` | dynamic-schemas | Document + SerializableStruct bridge |
| `ContentDocument` | dynamic-schemas | Schema-wrapping decorator for non-struct documents |
| `SchemaGuidedDocumentBuilder` | dynamic-schemas | `ShapeBuilder` for deserializing into `StructDocument` |
