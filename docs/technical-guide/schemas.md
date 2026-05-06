# Schemas

> **Last updated:** April 29, 2026

Schemas are a core innovation in Smithy-Java. A `Schema` is a trimmed-down, runtime representation of a Smithy model
shape that carries enough information to serialize, deserialize, and validate Java objects, without requiring the full
Smithy model at runtime. Schemas decouple protocols from generated types: a client can switch protocols (JSON, CBOR,
XML) at runtime because the protocol codec reads all format-specific behavior (field names, timestamp formats, etc.)
from the schema, not from hardcoded logic in the generated type.

**Source:**
  [`core/src/main/java/software/amazon/smithy/java/core/schema/`](https://github.com/smithy-lang/smithy-java/tree/main/core/src/main/java/software/amazon/smithy/java/core/schema)

## Class Hierarchy

`Schema` is an `abstract sealed class` with five permitted subclasses:

```
Schema (abstract sealed)
├── RootSchema          — Fully resolved, non-recursive shape (structures, scalars, enums, etc.)
├── MemberSchema        — A member targeting an already-built schema
├── DeferredRootSchema  — A possibly-recursive schema with lazily resolved members
├── DeferredMemberSchema — A member targeting an unbuilt SchemaBuilder (recursive reference)
└── ResolvedRootSchema  — Created when a DeferredRootSchema is fully resolved
```

All subclasses are package-private and `final`. External code interacts only with the `Schema` base class.

### Key Fields on Schema

Every `Schema` instance carries:

- `ShapeType type` — The Smithy shape type (STRUCTURE, LIST, STRING, etc.). Members report their *target's* type, never MEMBER.
- `ShapeId id` — The full Smithy shape ID (e.g., `com.example#MyStruct$fieldName` for members).
- `TraitMap traits` — Merged traits (target + member traits for member schemas). See [Trait Storage](#trait-storage) below.
- `String memberName` — Non-null only for member schemas.
- `int memberIndex` — Position in the parent's member list. Used for bitfield presence tracking.
- `boolean isRequiredByValidation` — True if the member has `@required` and no non-null default.
- Precomputed validation constraints — `minLengthConstraint`, `maxLengthConstraint`, `minRangeConstraint`,
  `maxRangeConstraint`, `stringPattern`, `stringValidationFlags`, etc. All computed at construction time for
  zero-allocation validation.
- `Supplier<ShapeBuilder<?>> shapeBuilder` — Factory for creating shape builders (used during deserialization).
- `Class<?> shapeClass` — The Java class associated with this schema.

## Creating Schemas

### Scalar Types

Static factory methods on `Schema` create simple shapes:

```java
Schema.createString(ShapeId.from("com.example#Name"), new LengthTrait(...))
Schema.createInteger(ShapeId.from("com.example#Count"))
Schema.createTimestamp(ShapeId.from("com.example#CreatedAt"))
Schema.createEnum(ShapeId.from("com.example#Color"), Set.of("RED", "GREEN", "BLUE"))
Schema.createIntEnum(ShapeId.from("com.example#Priority"), Set.of(1, 2, 3))
```

These return a `RootSchema` directly, no builder needed.

### Aggregate Types (Structures, Unions, Lists, Maps)

Aggregate types use a builder:

```java
Schema myStruct = Schema.structureBuilder(ShapeId.from("com.example#MyStruct"))
    .putMember("name", Schema.createString(ShapeId.from("com.example#MyStruct$name")),
               new RequiredTrait())
    .putMember("age", Schema.createInteger(ShapeId.from("com.example#MyStruct$age")))
    .builderSupplier(() -> new MyStruct.Builder())
    .shapeClass(MyStruct.class)
    .build();
```

`SchemaBuilder.build()` is idempotent, subsequent calls return the cached result. After `build()`, no more members can
be added.

### Prelude Schemas

[`PreludeSchemas`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/schema/PreludeSchemas.java) provides static constants for all Smithy prelude types:

```java
PreludeSchemas.STRING    // smithy.api#String
PreludeSchemas.INTEGER   // smithy.api#Integer
PreludeSchemas.BOOLEAN   // smithy.api#Boolean
PreludeSchemas.DOCUMENT  // smithy.api#Document
// ... and primitive variants with default traits:
PreludeSchemas.PRIMITIVE_BOOLEAN  // smithy.api#PrimitiveBoolean (default: false)
PreludeSchemas.PRIMITIVE_INTEGER  // smithy.api#PrimitiveInteger (default: 0)
```

## Lists and Maps

### List Schemas

A list schema must have exactly one member named `"member"`:

```java
Schema myList = Schema.listBuilder(ShapeId.from("com.example#NameList"))
    .putMember("member", Schema.createString(ShapeId.from("com.example#NameList$member")))
    .build();
```

The builder enforces this, passing any name other than `"member"` throws `IllegalArgumentException`. Build validation
also fails if the member is missing.

Access the element schema via the cached accessor:

```java
Schema elementSchema = myList.listMember(); // O(1), cached after first call
```

### Map Schemas

A map schema must have exactly two members named `"key"` and `"value"`:

```java
Schema myMap = Schema.mapBuilder(ShapeId.from("com.example#TagMap"))
    .putMember("key", Schema.createString(ShapeId.from("com.example#TagMap$key")))
    .putMember("value", Schema.createString(ShapeId.from("com.example#TagMap$value")))
    .build();
```

Access key/value schemas via cached accessors:

```java
Schema keySchema = myMap.mapKeyMember();     // O(1), cached
Schema valueSchema = myMap.mapValueMember(); // O(1), cached
```

### Member Map Optimization

The internal member storage uses optimized map implementations based on size:

| Members | Implementation |
|---------|---------------|
| 0 | `Map.of()` |
| 1 | `Map.of(name, schema)` |
| 2 | Custom `Map2<K,V>` (direct field comparison, faster than `Map.of` for 2 entries) |
| 3+ | `Map.ofEntries(...)` |

This matters because list schemas always have 1 member and map schemas always have 2, the most common cases get the
most optimized storage.

## Recursive Schema Handling

Recursive shapes (e.g., a tree node that contains children of the same type) require special handling because the schema
can't be fully built before it references itself.

### The Deferred Resolution Pattern

`SchemaBuilder.putMember()` has two overloads:

```java
// Normal: targets an already-built Schema
putMember(String name, Schema target, Trait... traits)

// Recursive: targets an unbuilt SchemaBuilder
putMember(String name, SchemaBuilder target, Trait... traits)
```

When `build()` is called, the builder checks if any member targets an unbuilt `SchemaBuilder`. If so, it creates a
`DeferredRootSchema` instead of a `RootSchema`.

### Resolution Flow

1. **Build phase**: `DeferredRootSchema` is created with unresolved member builders.
2. **Lazy resolution**: On first access to `members()`, `member()`, etc., `resolveInternal()` builds all member
   builders, creating `MemberSchema` or `DeferredMemberSchema` instances. Results are cached in a `volatile
   ResolvedMembers` record.
3. **Final resolution**: Calling `resolve()` creates a `ResolvedRootSchema` and updates the `SchemaBuilder` to point to
   it.
4. **Member access**: `DeferredMemberSchema.memberTarget()` calls `target.build()` on the `SchemaBuilder`, which returns
   the already-built schema (cached in `builtShape`).

### Example: Recursive Tree

```java
SchemaBuilder treeBuilder = Schema.structureBuilder(ShapeId.from("com.example#TreeNode"));
Schema leafSchema = Schema.createString(ShapeId.from("com.example#TreeNode$value"));

// Self-reference: children targets the builder, not a built schema
treeBuilder
    .putMember("value", leafSchema)
    .putMember("children", treeBuilder);  // recursive!

Schema treeSchema = treeBuilder.build();  // Returns DeferredRootSchema
```

In generated code, the codegen emits a two-phase pattern:

```java
// Phase 1: Create builder
static final SchemaBuilder TREE_NODE_BUILDER = Schema.structureBuilder(id);

// Phase 2: Add members (in static initializer block, after all builders exist)
TREE_NODE_BUILDER.putMember("value", LEAF_SCHEMA);
TREE_NODE_BUILDER.putMember("children", TREE_NODE_BUILDER); // self-reference

// Phase 3: Build and resolve
static final Schema TREE_NODE = TREE_NODE_BUILDER.build().resolve();
```

## Presence Tracking

Presence tracking determines which required members have been set during deserialization. This is critical for
validation, a structure missing a required member is invalid.

### PresenceTracker

[`PresenceTracker`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/schema/PresenceTracker.java)
is a `sealed abstract class` with three implementations:

| Implementation | When Used | Mechanism |
|---|---|---|
| `NoOpPresenceTracker` | 0 required members | All operations are no-ops (singleton) |
| `RequiredMemberPresenceTracker` | 1–64 required members | `long` bitfield |
| `BigRequiredMemberPresenceTracker` | 65+ required members | `java.util.BitSet` |

Usage in a generated builder:

```java
private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

public Builder name(String name) {
    this.name = name;
    tracker.setMember(SCHEMA_NAME); // sets bit for this member
    return this;
}

public MyStruct build() {
    tracker.checkAllSet(SCHEMA); // throws if any required member is missing
    return new MyStruct(this);
}
```

### How the Bitfield Works

Members are sorted during schema construction so that required-with-no-default members come first. Each member gets a
`memberIndex` (0-based position). The bitmask for a required member is `1L << memberIndex`.

- `setMember(Schema member)` → `setBitfields |= member.requiredByValidationBitmask()`
- `allSet()` → `schema.requiredStructureMemberBitfield() == setBitfields`

The `requiredStructureMemberBitfield()` is the OR of all required members' bitmasks, precomputed at schema construction
time.

### isRequiredByValidation

A member is required by validation only if:
1. It has the `@required` trait, AND
2. It either has no `@default` trait, or its default is `null`

This means a member with `@required` and `@default(0)` is NOT required by validation, the default satisfies the
requirement.

## Validation

The
[`Validator`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/schema/Validator.java)
validates shapes using their schemas. Its key design insight is that it **implements `ShapeSerializer`**, validation
piggybacks on the serialization visitor pattern.

### How It Works

```java
List<ValidationError> errors = Validator.validate(myStruct);
```

Internally:
1. Creates a `ShapeValidator` (implements `ShapeSerializer`)
2. Calls `shape.serialize(shapeValidator)`
3. Each `write*` method on the validator checks constraints from the schema
4. Errors are collected into a list

### What Gets Validated

| Constraint | Trait | Applies To |
|---|---|---|
| Type checking | (intrinsic) | All shapes |
| Required members | `@required` | Structure members |
| Length | `@length` | Strings (codepoint count), blobs, lists, maps |
| Range | `@range` | All numeric types |
| Pattern | `@pattern` | Strings |
| Enum values | `@enum` | String enums, int enums |
| Sparse | `@sparse` | Lists, maps (null element check) |
| Unique items | `@uniqueItems` | Lists |
| Union exactly-one | (intrinsic) | Unions |

### Precomputed Constraints

All constraint values are computed at schema construction time and stored as primitive fields. For example, range
validation for an integer member is a simple comparison against `minLongConstraint` / `maxLongConstraint`, no trait
lookup at validation time.

String validation uses a bitfield (`stringValidationFlags`) to skip unnecessary checks:

```java
static final int STRING_VALIDATE_LENGTH = 1;
static final int STRING_VALIDATE_ENUM = 2;
static final int STRING_VALIDATE_PATTERN = 4;
```

### Validation Errors

`ValidationError` is a sealed interface with record implementations for each error type:

- `RequiredValidationFailure` — missing required member
- `LengthValidationFailure` — length constraint violated
- `RangeValidationFailure` — range constraint violated
- `PatternValidationFailure` — regex pattern not matched
- `EnumValidationFailure` / `IntEnumValidationFailure` — invalid enum value
- `SparseValidationFailure` — null in non-sparse collection
- `TypeValidationFailure` — wrong type
- `UnionValidationFailure` — zero or multiple union members set
- `UniqueItemConflict` — duplicate items in `@uniqueItems` list
- `DepthValidationFailure` — exceeded max nesting depth

Each error carries a `path()` (JSON-pointer-style) and a `message()`.

### Configuration

The validator supports `maxDepth` (default 100) and `maxAllowedErrors` (default 100). When limits are reached, it
short-circuits via `ValidationShortCircuitException`.

## Trait Storage

### TraitKey

[`TraitKey<T>`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/schema/TraitKey.java)
is an identity-based key for O(1) trait access. Each trait class gets exactly one `TraitKey` with a unique integer `id`,
assigned via `ClassValue<TraitKey<?>>` for deduplication.

Pre-defined constants exist for ~40 commonly used traits:

```java
TraitKey.REQUIRED_TRAIT      // RequiredTrait.class
TraitKey.LENGTH_TRAIT        // LengthTrait.class
TraitKey.JSON_NAME_TRAIT     // JsonNameTrait.class
TraitKey.STREAMING_TRAIT     // StreamingTrait.class
TraitKey.HTTP_HEADER_TRAIT   // HttpHeaderTrait.class
// ... etc.
```

### TraitMap

`TraitMap` is a package-private, array-indexed trait storage. Internally it's a `Trait[]` array indexed by
`TraitKey.id`:

```java
TraitMap traits = TraitMap.create(new RequiredTrait(), new LengthTrait(1, 100));
RequiredTrait req = traits.get(TraitKey.REQUIRED_TRAIT); // O(1) array lookup
```

The array is sized to `largestTraitId + 1`, so it's compact for typical schemas.

### Trait Merging for Members

Member schemas carry *merged* traits, the target shape's traits overlaid with the member's own traits. This is done via
`TraitMap.withMemberTraits()`:

```java
// If target has @documentation("A string") and member has @required,
// the merged TraitMap contains both.
// If both have @documentation, the member's wins.
```

The `getTrait()` method on `Schema` always returns from the merged map. For member-only traits, use `getDirectTrait()`:

```java
schema.getTrait(TraitKey.REQUIRED_TRAIT);       // merged (target + member)
schema.getDirectTrait(TraitKey.REQUIRED_TRAIT);  // member-only
```

## Schema Extension System

Codecs and other components can attach lazily-computed data to schemas via the extension system.

### SchemaExtensionProvider

```java
public interface SchemaExtensionProvider<T> {
    SchemaExtensionKey<T> key();
    T provide(Schema schema);  // called lazily, result cached
}
```

Providers are discovered via `ServiceLoader`. Extensions are stored in an `Object[]` array on each `Schema`, initialized
with `NOT_COMPUTED` sentinels. On first access:

```java
T value = schema.getExtension(MyExtension.KEY);
```

The provider's `provide()` is called, and the result is cached. Thread safety relies on returned objects being immutable
(benign race pattern, multiple threads may compute the same value, but the result is always the same).

### Example: JSON Field Name Pre-computation

The JSON codec uses `SmithyJsonSchemaExtensions` to pre-compute UTF-8 byte arrays for field names (including quotes and
colon), hash tables for O(1) field resolution, and indexed field name tables by `memberIndex`. This data is computed
once per schema and reused across all serialization/deserialization calls.

## API-Level Classes

### SerializableShape and SerializableStruct

These interfaces connect schemas to the serialization system:

```java
// Any shape that can serialize itself
@FunctionalInterface
public interface SerializableShape {
    void serialize(ShapeSerializer encoder);
}

// A structure/union with schema awareness
public interface SerializableStruct extends SerializableShape {
    Schema schema();
    void serializeMembers(ShapeSerializer serializer);
    <T> T getMemberValue(Schema member);
}
```

Generated types implement `SerializableStruct`. Their `serializeMembers()` calls `serializer.write*(memberSchema,
value)` for each set member. The schema parameter on every write call is what enables protocol-agnostic serialization.

### ApiOperation

[`ApiOperation<I, O>`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/schema/ApiOperation.java)
represents a modeled operation with typed input/output:

```java
public interface ApiOperation<I extends SerializableStruct, O extends SerializableStruct> {
    ShapeBuilder<I> inputBuilder();
    ShapeBuilder<O> outputBuilder();
    Schema schema();
    Schema inputSchema();
    Schema outputSchema();
    TypeRegistry errorRegistry();
    List<ShapeId> effectiveAuthSchemes();
    // ...
}
```

### SchemaIndex

[`SchemaIndex`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/schema/SchemaIndex.java)
provides runtime lookup of schemas by `ShapeId`. Uses `ServiceLoader` to discover and combine multiple indexes (one per
generated package) into a `CombinedSchemaIndex`.

## Key Design Patterns

1. **Sealed class hierarchy** — `Schema` is sealed to 5 subclasses, enabling exhaustive pattern matching and preventing
   external extension.
2. **Precomputed validation** — All constraint values computed at construction time, stored as primitive fields for
   zero-allocation validation at runtime.
3. **Bitfield presence tracking** — Required members tracked via `long` bitfield (≤64 members) or `BitSet` (65+), with
   member sorting to pack required members into low indices.
4. **Array-indexed trait storage** — `TraitMap` uses `Trait[]` indexed by `TraitKey.id` for O(1) access, avoiding hash
   map overhead.
5. **Deferred resolution** — Recursive schemas use `DeferredRootSchema`/`DeferredMemberSchema` with lazy resolution,
   breaking circular dependencies.
6. **Serialization-based validation** — `Validator` implements `ShapeSerializer`, reusing the visitor pattern for
   validation without a separate traversal mechanism.
7. **Extension SPI** — `SchemaExtensionProvider` via `ServiceLoader` with lazy per-schema computation and benign-race
   caching.
