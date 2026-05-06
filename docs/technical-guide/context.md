# Context System

> **Last updated:** April 29, 2026

The `Context` is a typed key-value store that threads through the entire Smithy-Java client and server pipeline. It
carries configuration, per-call state, and subsystem-specific data using type-safe keys. Understanding the context
system is foundational, nearly every other component reads from or writes to a `Context`.

**Source:** [`context/`](https://github.com/smithy-lang/smithy-java/tree/main/context)

## Context Interface

`Context` is a **sealed interface** with two implementations:

- `ChunkedArrayStorageContext` — The mutable, default implementation
- `UnmodifiableContext` — A read-only wrapper/decorator

### Core API

```java
public sealed interface Context {
    <T> Context put(Key<T> key, T value);
    <T> T get(Key<T> key);
    default <T> T getOrDefault(Key<T> key, T defaultValue);
    default <T> T expect(Key<T> key);                // throws NullPointerException if missing
    default <T> Context putIfAbsent(Key<T> key, T value);
    default <T> T computeIfAbsent(Key<T> key, Function<Key<T>, ? extends T> mappingFunction);
    void copyTo(Context target);                     // deep copy via copyFunction
    default Context merge(Context other);            // returns a NEW context with both
}
```

### Factory Methods

```java
Context.create();                    // new mutable context
Context.empty();                     // singleton unmodifiable empty context
Context.modifiableCopy(context);     // deep copy, mutable
Context.unmodifiableCopy(context);   // deep copy, immutable
Context.unmodifiableView(context);   // live view (not a copy), immutable
```

## Context.Key&lt;T&gt;

`Context.Key<T>` is a final inner class providing identity-based, type-safe tokens for context values.

```java
static <T> Key<T> key(String name);                              // identity copy (shallow)
static <T> Key<T> key(String name, Function<T, T> copyFunction); // custom deep copy
```

Each key gets a monotonically increasing integer `id` used as an array index. Keys are registered in a global
`CopyOnWriteArrayList<Key>` with `synchronized` ID assignment.

**Critical rule:** Keys MUST be stored as `static final` fields. Creating ephemeral keys permanently grows the global
  registry and every context's storage.

### Copy Function

The `copyFunction` controls what happens during `copyTo()`. For immutable types, `Function.identity()` is used
(default). For mutable types, provide a copy constructor:

```java
// Immutable value, identity copy (default)
Context.Key<String> FOO = Context.key("Foo");

// Mutable value, deep copy
Context.Key<Set<String>> FEATURES = Context.key("Features", HashSet::new);
```

Without a proper copy function, `copyTo()` shares the same mutable object reference between source and target contexts.

## Storage Implementation

`ChunkedArrayStorageContext` uses a **chunked array** strategy for O(1) get/put by key ID:

- Keys are stored in 32-element chunks (`CHUNK_SIZE = 32`)
- Chunks are allocated lazily as needed
- Key ID is decomposed: `chunkIdx = id >> 5`, `offset = id & 0x1F`
- ~4.5x faster copies than HashMap, ~2x faster gets (per Javadoc)

`ChunkedArrayStorageContext` is **NOT thread-safe**. It's designed for single-threaded use within a request
pipeline. Key registration IS thread-safe.

## Context Lifecycle in the Client Pipeline

```
ClientConfig.Builder.context (mutable)
    ↓ build()
ClientConfig.context (unmodifiable copy, safe to share across threads)
    ↓ ClientCall.Builder.withConfig()
ClientCall.context = Context.modifiableCopy(config.context)  (mutable per-call copy)
    ↓ enriched by pipeline
    ├── CallContext.RETRY_ATTEMPT, FEATURE_IDS, IDENTITY, ENDPOINT, etc.
    ↓ passed to subsystems
    ├── Context.unmodifiableView() → AuthSchemeResolverParams
    ├── Context.unmodifiableView() → EndpointResolverParams
    ├── mutable reference → transport.send()
    └── mutable reference → all interceptor hooks
```

This ensures:
1. **Client-level config is immutable**, safe to share across threads/calls
2. **Per-call context is mutable**, enriched as the pipeline progresses
3. **Subsystem views are read-only**, auth/endpoint resolvers get unmodifiable views
4. **Deep copies isolate mutations**, `modifiableCopy` + `copyFunction` prevent cross-call contamination

## Settings Pattern

Settings are defined as interfaces with `Context.Key` constants and convenience setter methods:

```java
public interface RegionSetting<B extends ClientSetting<B>> extends ClientSetting<B> {
    Context.Key<String> REGION = Context.key("Region name");

    default void region(String region) {
        putConfig(REGION, region);
    }
}
```

Settings compose via interface inheritance:

```java
public interface SigV4Settings<B> extends ClockSetting<B>, RegionSetting<B> { ... }
```

This allows client builders and `RequestOverrideConfig.OverrideBuilder` to mix in typed settings while storing all values in the same `Context` instance.

## Key Context Keys

### Core Client Keys (`CallContext`)

| Key | Type | Description |
|-----|------|-------------|
| `ENDPOINT` | `Endpoint` | Resolved endpoint |
| `ENDPOINT_RESOLVER` | `EndpointResolver` | The resolver used |
| `IDENTITY` | `Identity` | Resolved caller identity |
| `RETRY_ATTEMPT` | `Integer` | Current retry attempt (starts at 1) |
| `RETRY_MAX` | `Integer` | Max retries configured |
| `IDEMPOTENCY_TOKEN` | `String` | Token used with the call |
| `FEATURE_IDS` | `Set<FeatureId>` | Feature IDs (uses `HashSet::new` copy function) |

### Client Configuration Keys (`ClientContext`)

| Key | Type | Description |
|-----|------|-------------|
| `APPLICATION_ID` | `String` | App name for user-agent |
| `API_CALL_TIMEOUT` | `Duration` | Total call timeout including retries |
| `API_CALL_ATTEMPT_TIMEOUT` | `Duration` | Single attempt timeout |

### HTTP Keys (`HttpContext`)

| Key | Type | Description |
|-----|------|-------------|
| `HTTP_REQUEST_TIMEOUT` | `Duration` | HTTP-level request timeout |
| `REQUEST_MIN_COMPRESSION_SIZE_BYTES` | `Integer` | Compression threshold |
| `DISABLE_REQUEST_COMPRESSION` | `Boolean` | Disable compression flag |

### AWS-Specific Keys

| Key | Type | Location |
|-----|------|----------|
| `RegionSetting.REGION` | `String` | aws-client-core |
| `SigV4Settings.SIGNING_NAME` | `String` | aws-sigv4 |
| `EndpointSettings.USE_DUAL_STACK` | `Boolean` | aws-client-core |
| `EndpointSettings.USE_FIPS` | `Boolean` | aws-client-core |

### Other Notable Keys

| Key | Type | Location |
|-----|------|----------|
| `ModelSetting.MODEL` | `Model` | dynamic-client |
| `ServiceIdSetting.SERVICE_ID` | `ShapeId` | dynamic-client |
| `RulesEngineSettings.BYTECODE` | `Bytecode` | rulesengine |
| `EndpointContext.CUSTOM_ENDPOINT` | `Endpoint` | endpoints |
