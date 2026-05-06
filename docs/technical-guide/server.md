# Server

> **Last updated:** April 29, 2026

The server side of Smithy-Java provides a framework for building HTTP services from Smithy models. It's still under
development and marked as `@SmithyUnstableApi`. The architecture follows the same schema-driven, protocol-agnostic
design as the client side, protocols are pluggable via SPI, and generated server stubs use the same type system as
generated clients.

**Source:** [`server/`](https://github.com/smithy-lang/smithy-java/tree/main/server)

## Module Structure

| Module | Purpose |
|---|---|
| `server-api` | Public interfaces: `Service`, `Operation`, `Server`, `ServerBuilder`, `Route`, `RequestContext` |
| `server-core` | Internal runtime: orchestration, handler pipeline, protocol resolution, HTTP request/response |
| `server-netty` | Netty-based HTTP/1.1 server implementation |
| `server-rpcv2-cbor` | Server-side `rpcv2-cbor` protocol |
| `server-proxy` | `ProxyService`, proxies to a downstream service via `DynamicClient` |

Additionally, `aws/server/aws-server-restjson/` provides the AWS `restJson1` server protocol.

## Core Abstractions

### Service

```java
public interface Service {
    <I extends SerializableStruct, O extends SerializableStruct>
        Operation<I, O> getOperation(String operationName);
    List<Operation<?, ?>> getAllOperations();
    Schema schema();
    TypeRegistry typeRegistry();
    SchemaIndex schemaIndex();
}
```

A `Service` holds a collection of `Operation` instances, a `Schema` (the Smithy service shape), a `TypeRegistry` (for
error types), and a `SchemaIndex`. Generated server stubs implement this interface.

### Operation

```java
public final class Operation<I extends SerializableStruct, O extends SerializableStruct> {
    // Sync
    public static <I, O> Operation<I, O> of(
        String name, BiFunction<I, RequestContext, O> operation,
        ApiOperation<I, O> sdkOperation, Service service);

    // Async
    public static <I, O> Operation<I, O> ofAsync(
        String name, BiFunction<I, RequestContext, CompletableFuture<O>> operation,
        ApiOperation<I, O> sdkOperation, Service service);

    public boolean isAsync();
    public String name();
    public ApiOperation<I, O> getApiOperation();
    public Service getOwningService();
}
```

Each `Operation` wraps either a sync `BiFunction<I, RequestContext, O>` or an async `BiFunction<I, RequestContext,
CompletableFuture<O>>`. It also holds an `ApiOperation` (the codegen-generated schema for the operation) and a
back-reference to its owning `Service`.

### Server and ServerBuilder

```java
public interface Server {
    static ServerBuilder<?> builder();                    // SPI-discovered
    static ServerBuilder<?> builder(String serverName);   // Named lookup
    void start();
    CompletableFuture<Void> shutdown();
}
```

`Server.builder()` uses `ServiceLoader` to discover `ServerProvider` implementations. Currently the only provider is `NettyServerProvider` (name `"smithy-java-netty-server"`).

`ServerBuilder` provides a fluent API:

```java
Server server = Server.builder()
    .endpoints(URI.create("http://localhost:8080"))
    .addService(myService)
    .addService("/api/v2", anotherService)  // with path prefix
    .numberOfWorkers(4)
    .build();

server.start();
```

### Route

```java
public final class Route {
    String getHostName();
    String getPathPrefix();
    Integer getPort();
    String getProtocol();
    List<Service> getServices();
}
```

Routes enable virtual-host-style routing: requests are matched by hostname, port, protoqcol scheme, and path prefix to
determine which services are candidates.

### RequestContext

```java
public interface RequestContext {
    String getRequestId();
}
```

Currently minimal, marked with `// TODO Fill with more stuff`. The `OperationHandler` currently passes `null` for the
context when invoking operations.

## Operation Dispatch Flow

1. **HTTP Request arrives** at Netty → `HttpRequestHandler.channelRead()`
2. **Protocol Resolution**: `ProtocolResolver.resolve()` is called
   - `ServiceMatcher.getCandidateServices()` narrows services by route matching
   - Each `ServerProtocol` (loaded via SPI) tries `resolveOperation()`, first non-null result wins
   - Returns `ServiceProtocolResolutionResult(service, operation, protocol)`
3. **Job Creation**: An `HttpJob` is created with the resolved operation, protocol, request, and response
4. **Body Accumulation**: HTTP content chunks are accumulated into a `ByteArrayOutputStream`
5. **Orchestrator Enqueue**: On `LastHttpContent`, the job is enqueued to the `Orchestrator`
6. **Handler Pipeline** (inside `SingleThreadOrchestrator`):
   - `ProtocolHandler.before()` → `protocol.deserializeInput(job)`
   - `ValidationHandler.before()` → validates the deserialized input using `Validator`
   - `OperationHandler.before()` → invokes the user's sync/async function, sets the response
   - Then `after()` in reverse order:
   - `ProtocolHandler.after()` → `protocol.serializeOutput(job, output)` or `protocol.serializeError(job, error)`
7. **Response Writing**: `HttpRequestHandler.writeResponse()` converts the serialized `DataStream` to a Netty
   `DefaultFullHttpResponse`

## The Netty HTTP Server

### NettyServer

Uses Netty's `ServerBootstrap` with platform-optimal transport:
- **Epoll** (Linux) → **KQueue** (macOS) → **NIO** (fallback)
- Boss group: 1 thread for accepting connections
- Worker group: `availableProcessors() * 2` threads for I/O
- Creates an `OrchestratorGroup` with `ErrorHandlingOrchestrator(SingleThreadOrchestrator(...))` per worker

### ServerChannelInitializer

```java
void initChannel(Channel channel) {
    pipeline.addLast("http1Codec", new HttpServerCodec());
    pipeline.addLast(new HttpRequestHandler(orchestratorGroup.next(), protocolResolver));
}
```

Each new channel gets an HTTP/1.1 codec and an `HttpRequestHandler` bound to a specific orchestrator (round-robin
selected).

### HttpRequestHandler

A `ChannelDuplexHandler` that:
1. On `HttpRequest`: creates server-side `HttpRequest`, resolves protocol/operation, creates `HttpJob`
2. On `HttpContent`: accumulates body bytes into `ByteArrayOutputStream`
3. On `LastHttpContent`: wraps body as `DataStream`, enqueues job to orchestrator
4. On completion: writes `DefaultFullHttpResponse` back to channel, including CORS headers

## Protocols

### Protocol SPI

Protocols are discovered via `ServiceLoader<ServerProtocolProvider>`:

```java
public interface ServerProtocolProvider {
    ServerProtocol provideProtocolHandler(List<Service> candidateServices);
    ShapeId getProtocolId();
    int precision();  // lower = tried first
}
```

### ServerProtocol (Abstract Base)

```java
public abstract class ServerProtocol {
    public abstract ShapeId getProtocolId();
    public abstract ServiceProtocolResolutionResult resolveOperation(
        ServiceProtocolResolutionRequest request, List<Service> candidates);
    public abstract CompletableFuture<Void> deserializeInput(Job job);
    protected abstract CompletableFuture<Void> serializeOutput(
        Job job, SerializableStruct output, boolean isError);
}
```

### RpcV2 CBOR Protocol

- **Resolution**: Checks `smithy-protocol: rpc-v2-cbor` header + POST method. Parses URL path:
  `/service/{ServiceName}/operation/{OperationName}`
- **Deserialization**: Uses `Rpcv2CborCodec` to deserialize CBOR body
- **Serialization**: Uses `Rpcv2CborCodec` to serialize output to CBOR

### AWS RestJson1 Protocol

- **Resolution**: Builds a `UriMatcherMap` (trie-based URI router) from all operations' `@http` traits. Matches by HTTP
  method + URI pattern (including path labels and query strings)
- **Deserialization**: Uses `HttpBinding.requestDeserializer()` with `JsonCodec`, handles path labels, query params,
  headers, and JSON body
- **Serialization**: Uses `HttpBinding.responseSerializer()` with `JsonCodec`

## Handler Pipeline

### Handler Interface

```java
public interface Handler {
    CompletableFuture<Void> before(Job job);
    CompletableFuture<Void> after(Job job);
}
```

Handlers form a pipeline with `before()` called in order and `after()` called in reverse (like a stack). If any
`before()` fails, the pipeline short-circuits to the `after()` phase.

### Built-in Handlers

Assembled by `HandlerAssembler`:

```java
List.of(
    new ProtocolHandler(),      // 1st: deserialize input / serialize output
    new ValidationHandler(),    // 2nd: validate deserialized input
    new OperationHandler()      // 3rd: invoke user's operation function
);
```

1. **ProtocolHandler**: `before()` → deserialize input. `after()` → serialize output or error.
2. **ValidationHandler**: `before()` → runs `Validator.validate(input)`, throws `ValidationException` on failure.
3. **OperationHandler**: `before()` → invokes the user's sync/async function, sets response value.

### Orchestrator Hierarchy

```
Orchestrator (sealed interface)
  └── ObservableOrchestrator (sealed, adds inflightJobs())
        ├── SingleThreadOrchestrator, runs handler pipeline on dedicated thread
        ├── OrchestratorGroup, manages pool with selection strategy
        └── DelegatingObservableOrchestrator (sealed, decorator base)
              └── ErrorHandlingOrchestrator, catches unhandled errors
```

- **SingleThreadOrchestrator**: Dedicated daemon thread consuming from a `LinkedBlockingDeque<Runnable>`. Each job walks
  the handler pipeline as a state machine (BEFORE → AFTER → DONE), re-enqueuing itself when async handlers return
  incomplete futures.
- **OrchestratorGroup**: Pool with pluggable selection strategies:
  - `RoundRobinStrategy`, simple counter-based
  - `LeastLoadedStrategy`, picks orchestrator with fewest inflight jobs

## Server Stub Generation

The codegen generates two types of artifacts for server mode:

### Operation Interfaces

For each operation, two `@FunctionalInterface` interfaces are generated:

```java
// Sync
@FunctionalInterface
public interface AddBeerOperation {
    AddBeerOutput addBeer(AddBeerInput input, RequestContext context);
}

// Async
@FunctionalInterface
public interface AddBeerOperationAsync {
    CompletableFuture<AddBeerOutput> addBeer(AddBeerInput input, RequestContext context);
}
```

### Service Implementation

A concrete `Service` implementation with a **staged builder** pattern:

```java
// Each stage enforces that all operations are provided
public interface GetBeerStage {
    AddBeerStage addGetBeerOperation(GetBeerOperation op);
    AddBeerStage addGetBeerOperation(GetBeerOperationAsync op);
}
public interface AddBeerStage {
    BuildStage addAddBeerOperation(AddBeerOperation op);
    BuildStage addAddBeerOperation(AddBeerOperationAsync op);
}
public interface BuildStage {
    BeerService build();
}
```

Usage:

```java
BeerService service = BeerService.builder()
    .addGetBeerOperation(new GetBeerImpl())     // sync
    .addAddBeerOperation(new AddBeerAsyncImpl()) // async
    .build();
```

## Additional Components

### FilteredService

```java
public final class FilteredService implements Service {
    public FilteredService(Service delegate, Predicate<ApiOperation<?, ?>> filter);
}
```

Wraps a `Service` and filters its operations. Used with `OperationFilters.allowList()` / `OperationFilters.blockList()`.

### ProxyService

A `Service` implementation that proxies requests to a downstream service using `DynamicClient`. Supports model-driven
operation discovery, configurable auth/region/endpoint, and `ProxyOperationTrait` for input wrapping/unwrapping.

### Job Hierarchy

```
Job (sealed interface)
  └── DefaultJob (abstract sealed, holds operation + protocol + failure)
        └── HttpJob (final, adds HttpRequest + HttpResponse)
```

`Request` and `Response` are sealed interfaces carrying a `Context` (key-value bag), a `DataStream` (serialized bytes),
and a deserialized/serialized `SerializableStruct`.

## Current Limitations

The server is in developer preview. Known limitations from code comments:

1. **RequestContext not wired** — `OperationHandler` passes `null` for `RequestContext`
2. **Full body buffering** — Entire request body accumulated in memory before processing
3. **No max body size** — No limit on request body size
4. **HTTP/1.1 only** — No HTTP/2 support
5. **No TLS support** — Plain HTTP only
6. **No user-facing middleware API** — The `Handler` pipeline is internal; users cannot add custom handlers
7. **No graceful drain** — `shutdown()` returns immediately without draining inflight requests
8. **HandlerAssembler is static** — No user-extensible handler registration

## Key Classes Summary

| Class | Module | Role |
|---|---|---|
| `Service` | server-api | Core interface for a Smithy service with operations |
| `Operation` | server-api | Wraps a sync/async user function + ApiOperation metadata |
| `Server` / `ServerBuilder` | server-api | SPI-based server lifecycle + configuration |
| `Route` / `ServiceMatcher` | server-api/core | Request-to-service routing |
| `ServerProtocol` | server-core | Abstract base for protocol implementations |
| `ServerProtocolProvider` | server-core | SPI interface for protocol discovery |
| `ProtocolResolver` | server-core | Iterates protocols to resolve request → operation |
| `Handler` | server-core | Pipeline step with `before()`/`after()` lifecycle |
| `ProtocolHandler` | server-core | Delegates to protocol for (de)serialization |
| `ValidationHandler` | server-core | Validates deserialized input |
| `OperationHandler` | server-core | Invokes user's operation function |
| `SingleThreadOrchestrator` | server-core | Runs handler pipeline on a dedicated thread |
| `OrchestratorGroup` | server-core | Pool of orchestrators with selection strategies |
| `ErrorHandlingOrchestrator` | server-core | Catches unhandled errors and serializes them |
| `Job` / `HttpJob` | server-core | Request processing unit |
| `NettyServer` | server-netty | Netty-based server implementation |
| `HttpRequestHandler` | server-netty | Netty channel handler bridging HTTP to orchestrator |
| `RpcV2CborProtocol` | server-rpcv2-cbor | Server-side rpcv2-cbor protocol |
| `AwsRestJson1Protocol` | aws-server-restjson | Server-side AWS restJson1 with URI trie router |
| `FilteredService` | server-api | Decorator that filters operations by predicate |
| `ProxyService` | server-proxy | Proxies to downstream via DynamicClient |
| `OperationInterfaceGenerator` | codegen-plugin | Generates sync/async operation interfaces |
| `ServiceGenerator` | codegen-plugin | Generates Service implementation with staged builder |
