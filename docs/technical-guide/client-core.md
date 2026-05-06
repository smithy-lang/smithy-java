# Client Core Architecture

> **Last updated:** April 29, 2026

The client core is the backbone of all Smithy-Java clients, both generated and dynamic. It provides the `Client` base
class, the plugin system, the interceptor system, and the call pipeline that orchestrates request execution from
serialization through auth, endpoint resolution, signing, transport, and deserialization.

**Source:** [`client/client-core/`](https://github.com/smithy-lang/smithy-java/tree/main/client/client-core)

## Client Base Class

[`Client`](https://github.com/smithy-lang/smithy-java/blob/main/client/client-core/src/main/java/software/amazon/smithy/java/client/core/Client.java)
is the abstract base for all generated clients. It holds immutable runtime state:

```java
public abstract class Client implements Closeable {
    private final ClientConfig config;
    private final ClientPipeline<?, ?> pipeline;
    private final TypeRegistry typeRegistry;
    private final ClientInterceptor interceptor;
    private final IdentityResolvers identityResolvers;
    private final RetryStrategy retryStrategy;
}
```

### The call() Method

```java
protected <I extends SerializableStruct, O extends SerializableStruct> O call(
    I input, ApiOperation<I, O> operation, RequestOverrideConfig overrideConfig)
```

This is the entry point for all RPC calls:
1. Applies `RequestOverrideConfig` if present
2. Invokes `interceptor.modifyBeforeCall()` to allow interceptors to modify the config
3. Rebuilds pipeline/resolvers if config changed
4. Constructs a `ClientCall` with all resolved state
5. Delegates to `pipeline.send(clientCall)`

### Client.Builder

A self-referential generic builder (`B extends Builder<I, B>`) implementing `ClientSetting<B>`. Wraps a
`ClientConfig.Builder` internally:

```java
builder.transport(new JavaHttpClientTransport())
    .protocol(new Rpcv2CborProtocol(serviceId))
    .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
    .addInterceptor(myInterceptor)
    .retryStrategy(StandardRetryStrategy.builder().maxAttempts(5).build())
    .addPlugin(myPlugin)
    .build();
```

## Plugin System

### ClientPlugin

```java
@FunctionalInterface
public interface ClientPlugin {
    void configureClient(ClientConfig.Builder config);
    default List<ClientPlugin> getChildPlugins() { return Collections.emptyList(); }
    default Phase getPluginPhase() { return Phase.APPLY; }
}
```

**Phase enum** (execution order):
1. `FIRST` — framework-level plugins (e.g., `DefaultPlugin`)
2. `BEFORE_DEFAULTS` → `DEFAULTS` → `AFTER_DEFAULTS`
3. `BEFORE_APPLY` → `APPLY` (default) → `AFTER_APPLY`
4. `LAST` — framework-level finalization

Plugins are deduplicated by class (first instance wins). Applied plugin classes are tracked so `toBuilder().build()`
won't re-apply them.

### AutoClientPlugin (SPI)

```java
public interface AutoClientPlugin extends ClientPlugin {}
```

Discovered via `ServiceLoader`. Applied through `AutoPlugin` which is a child of `DefaultPlugin`.

### DefaultPlugin

Singleton at `Phase.FIRST`. Always added to every client. Its children handle:
- **`DiscoverTransportPlugin`** — auto-discovers transport via SPI
- **`ApplyModelRetryInfoPlugin`** — sets `RetryInfo` on exceptions based on modeled retry info
- **`InjectIdempotencyTokenPlugin`** — auto-generates idempotency tokens when not provided
- **`AutoPlugin`** — loads all `AutoClientPlugin` SPI implementations as children

### Plugin Collection Algorithm

1. Recursively flatten plugins + children
2. Skip already-applied classes
3. Filter through `pluginPredicate`
4. Sort by `Phase` (stable sort preserves insertion order within same phase)
5. Call `configureClient(builder)` on each

## Interceptor System

### ClientInterceptor

Defines ~20 hooks that inject code into the request execution pipeline:

| Hook | Type | Scope |
|------|------|-------|
| `modifyBeforeCall` | write | once/execution |
| `readBeforeExecution` | read | once/execution |
| `modifyBeforeSerialization` → `readBeforeSerialization` | write/read | once/execution |
| `readAfterSerialization` | read | once/execution |
| `modifyBeforeRetryLoop` | write | once/execution |
| `readBeforeAttempt` | read | once/attempt |
| `modifyBeforeSigning` → `readBeforeSigning` → `readAfterSigning` | write/read | once/attempt |
| `modifyBeforeTransmit` → `readBeforeTransmit` | write/read | once/attempt |
| `readAfterTransmit` | read | once/attempt |
| `modifyBeforeDeserialization` → `readBeforeDeserialization` | write/read | once/attempt |
| `readAfterDeserialization` | read | once/attempt |
| `modifyBeforeAttemptCompletion` → `readAfterAttempt` | write/read | once/attempt |
| `modifyBeforeCompletion` → `readAfterExecution` | write/read | once/execution |

**Read hooks** observe in-flight data (cannot modify). **Write hooks** can modify and return new values.

### Hook Data Types

Sealed hierarchy carrying progressively more data:

```
CallHook<I, O>          — operation, config, input
InputHook<I, O>         — operation, context, input
  └─ RequestHook        — adds request
       └─ ResponseHook  — adds response
            └─ OutputHook — adds output
```

Each hook has `with*()` methods that return a new hook if the value changed (identity-check optimization).

### ClientInterceptorChain

Chains multiple interceptors. For read hooks, catches exceptions with "last error wins" semantics. For write hooks,
chains output of one interceptor as input to the next.

## Call Pipeline

[`ClientPipeline`](https://github.com/smithy-lang/smithy-java/blob/main/client/client-core/src/main/java/software/amazon/smithy/java/client/core/ClientPipeline.java)
orchestrates the full request lifecycle. Created via `ClientPipeline.of(protocol, transport)` which validates that both
share the same `MessageExchange`.

### Full Flow

**Pre-retry (once per execution):**
1. `readBeforeExecution` → `modifyBeforeSerialization` → `readBeforeSerialization`
2. `protocol.createRequest(operation, input, context, UNRESOLVED_URI)` → serialized request
3. `readAfterSerialization` → `modifyBeforeRetryLoop`

**Retry token acquisition:**
4. `retryStrategy.acquireInitialToken()` → may sleep for pre-emptive throttling

**Per-attempt loop:**
5. `readBeforeAttempt` → `modifyBeforeSigning` → `readBeforeSigning`
6. **Auth scheme resolution** → resolve identity → resolve endpoint → apply endpoint overrides
7. `protocol.setServiceEndpoint(request, endpoint)` → merge endpoint into request
8. `signer.sign(request, identity, signerProperties)` → signed request
9. Activate event stream writer if applicable
10. `readAfterSigning` → `modifyBeforeTransmit` → `readBeforeTransmit`
11. **`transport.send(context, request)`** → response
12. `readAfterTransmit` → `modifyBeforeDeserialization` → `readBeforeDeserialization`
13. `protocol.deserializeResponse(...)` → output or error
14. `readAfterDeserialization` → `modifyBeforeAttemptCompletion` → `readAfterAttempt`

**Retry decision:**
15. If retryable error and stream is replayable: `retryStrategy.refreshRetryToken()` → sleep → back to step 5
16. If not retryable: `retryStrategy.recordSuccess()` (on success)

**Completion (once per execution):**
17. `modifyBeforeCompletion` → `readAfterExecution`
18. Return output or throw error

## Transport Abstraction

```java
public interface ClientTransport<RequestT, ResponseT> extends Closeable {
    ResponseT send(Context context, RequestT request);
    MessageExchange<RequestT, ResponseT> messageExchange();
}
```

`ClientTransportFactory` is the SPI for discovering transports with priority ordering. `remapExceptions()` maps JDK
exceptions to the `TransportException` hierarchy.

## Protocol Abstraction

```java
public interface ClientProtocol<RequestT, ResponseT> {
    ShapeId id();
    Codec payloadCodec();
    MessageExchange<RequestT, ResponseT> messageExchange();
    <I, O> RequestT createRequest(ApiOperation<I, O> operation, I input, Context context, SmithyUri endpoint);
    RequestT setServiceEndpoint(RequestT request, Endpoint endpoint);
    <I, O> O deserializeResponse(ApiOperation<I, O> operation, Context context, TypeRegistry errorRegistry,
                                  RequestT request, ResponseT response);
}
```

`ClientProtocolFactory<T extends Trait>` is the SPI for creating protocols from Smithy traits.

## RequestOverrideConfig

Allows per-request configuration overrides. Contains the same elements as `ClientConfig` but all optional:

```java
RequestOverrideConfig override = RequestOverrideConfig.builder()
    .endpointResolver(EndpointResolver.staticEndpoint("https://other.com"))
    .addInterceptor(loggingInterceptor)
    .build();

Output result = client.call("GetItem", input, override);
```

Application: creates a new `ClientConfig` by copying the original builder, applying non-null overrides (additive for
interceptors, auth schemes, identity resolvers), and rebuilding.

## Error Hierarchy

```
CallException
  └─ TransportException
       ├─ ConnectTimeoutException
       ├─ ConnectionAcquireTimeoutException
       ├─ ConnectionClosedException
       ├─ TlsException
       ├─ TransportProtocolException
       ├─ TransportSocketException
       └─ TransportSocketTimeout
```

## Key Classes Summary

| Class | Role |
|-------|------|
| `Client` | Abstract base for all clients. Entry point via `call()`. |
| `Client.Builder` | Abstract builder wrapping `ClientConfig.Builder`. |
| `ClientConfig` | Immutable snapshot of all client configuration. |
| `ClientPipeline` | Orchestrates the full request lifecycle. |
| `ClientCall` | Per-call data bag (input, operation, config, retry state). |
| `ClientPlugin` | Functional interface to modify `ClientConfig.Builder`. |
| `AutoClientPlugin` | SPI marker for auto-discovered plugins. |
| `DefaultPlugin` | Singleton FIRST-phase plugin (transport, retry, idempotency, auto-plugins). |
| `ClientInterceptor` | ~20 hook methods for observing/modifying the call pipeline. |
| `ClientProtocol` | Serializes input → request, deserializes response → output. |
| `ClientTransport` | Sends serialized requests and returns responses. |
| `MessageExchange` | Marker ensuring protocol/transport compatibility. |
| `RequestOverrideConfig` | Per-request configuration overrides. |
| `CallContext` | Context keys: ENDPOINT, IDENTITY, RETRY_ATTEMPT, etc. |
