# Smithy-Java Knowledge Center

> **Last updated:** April 29, 2026

A collection of technical documents covering the critical subsystems of Smithy-Java. Each document provides enough depth
to understand, maintain, and extend the subsystem, including architecture, key classes, design patterns, and links to
source code.

**Repository:** [github.com/smithy-lang/smithy-java](https://github.com/smithy-lang/smithy-java)

## Suggested Reading Order

The documents are organized in dependency order. Start with the foundations, then work through the client stack, and
finish with the specialized systems.

### Tier 1, Foundations

Read these first. Every other document assumes familiarity with these
concepts.

| # | Document | Summary |
|---|----------|---------|
| 1 | [Context System](context.md) | The `Context` typed key-value store that threads through the entire pipeline. Covers `Context.Key<T>`, chunked array storage, copy semantics, and the settings pattern. |
| 2 | [Schemas](schemas.md) | The `Schema` sealed class hierarchy, runtime representations of Smithy shapes. Covers lists/maps, recursive schemas, presence tracking, validation, trait storage (`TraitKey`/`TraitMap`), and the extension system. |
| 3 | [I/O and Streaming](io.md) | `DataStream` and `EventStream` abstractions. Covers frame encoding/decoding, `EventPipeStream` producer-consumer bridge, and how streaming flows through the pipeline. |

### Tier 2, Serialization

These build on Schemas and I/O.

| # | Document | Summary | Depends On |
|---|----------|---------|------------|
| 4 | [Codecs](codecs.md) | `Codec`, `ShapeSerializer`, `ShapeDeserializer` interfaces. JSON (native high-perf impl with speculative matching), CBOR (canonicalizer), XML (StAX with flattened member buffering). | Schemas |
| 5 | [Documents](documents.md) | The `Document` interface for untyped Smithy data. The serialize/serializeContents two-level pattern, `LazyStructure` optimization, `DocumentParser`/`DocumentDeserializer`. | Schemas, Codecs |

### Tier 3, Client Stack

Read these in order. Each builds on the previous.

| # | Document | Summary | Depends On |
|---|----------|---------|------------|
| 6 | [HTTP Layer](http.md) | HTTP abstraction (`HttpRequest`/`HttpResponse`), HTTP binding (mapping `@http*` traits), Java HttpClient transport, `HttpBindingClientProtocol`. | Schemas, Codecs, I/O |
| 7 | [Auth System](auth.md) | `Identity`, `IdentityResolver`, `Signer`, `AuthScheme`, `AuthSchemeResolver`. SigV4 implementation as concrete example. | Context, HTTP |
| 8 | [Client Core](client-core.md) | `Client` base class, plugin system (`ClientPlugin`, phases, `AutoClientPlugin`), interceptor system (~20 hooks), the full call pipeline, transport/protocol abstractions, `RequestOverrideConfig`. | Context, Schemas, HTTP, Auth |
| 9 | [Retries and Waiters](retries-waiters.md) | Token-based retry strategy (Standard, Adaptive with CUBIC rate limiting), token buckets, waiter framework with JMESPath matchers, waiter codegen. | Client Core |

### Tier 4, Protocol Implementations

These can be read independently after the client stack.

| # | Document | Summary | Depends On |
|---|----------|---------|------------|
| 10 | [AWS Protocol Integrations](aws-protocols.md) | AWS JSON 1.0/1.1, restJson1, restXml, AWS Query protocols. Event streaming infrastructure. Comparison table. | HTTP, Codecs, Client Core |

### Tier 5, Advanced Systems

These can be read in any order after the client stack.

| # | Document | Summary | Depends On |
|---|----------|---------|------------|
| 11 | [Dynamic Client](dynamic-client.md) | `DynamicClient`, creating clients at runtime from Smithy models. `SchemaConverter`, `StructDocument` bridge, `ContentDocument`, `SchemaGuidedDocumentBuilder`. | Client Core, Documents, Schemas |
| 12 | [Rules Engine](rules-engine.md) | Endpoint resolution via BDD transformation + bytecode VM. Compilation pipeline, fused opcodes, inline condition optimization, register allocation, context providers. | Client Core |
| 13 | [Server](server.md) | Server-side framework. `Service`/`Operation` interfaces, handler pipeline, Netty integration, protocol SPI, orchestrator hierarchy, server stub codegen. Current limitations. | Schemas, Codecs, HTTP |
| 14 | [MCP](mcp.md) | Model Context Protocol integration. Converting Smithy operations to MCP tools, JSON Schema generation, stdio/HTTP transports, proxy aggregation, `@prompts` trait. | Server, Documents |

### Tier 6, Code Generation

Best read after understanding what the generated code looks like (from the client/server docs).

| # | Document | Summary | Depends On |
|---|----------|---------|------------|
| 15 | [Code Generation](codegen.md) | The `java-codegen` plugin. Modes (client/server/types), directed codegen pattern, `JavaWriter` format patterns (`$C`, `$T`), synthetic service trick, integration system, type generators. | Schemas, Client Core, Server |

## Dependency Graph

```
Context ─────────────────────────────────────────────────┐
   │                                                     │
Schemas ──────────────────────────────────┐              │
   │                                      │              │
I/O ──────────────────────┐               │              │
   │                      │               │              │
Codecs ───────────┐       │               │              │
   │              │       │               │              │
Documents         │       │               │              │
   │              │       │               │              │
   │         HTTP Layer ──┤               │              │
   │              │       │               │              │
   │         Auth System ─┤               │              │
   │              │       │               │              │
   │         Client Core ─┼───────────────┤              │
   │              │       │               │              │
   │         Retries/Waiters              │              │
   │              │                       │              │
   │         AWS Protocols                │              │
   │                                      │              │
Dynamic Client                       Server ─── MCP      │
                                          │              │
Rules Engine                         Code Generation ────┘
```

## Quick Reference

| Document | Key Classes | Module(s) |
|----------|-------------|-----------|
| Context | `Context`, `Context.Key<T>` | `context` |
| Schemas | `Schema`, `SchemaBuilder`, `TraitKey`, `TraitMap`, `PresenceTracker`, `Validator` | `core` |
| I/O | `DataStream`, `EventStream`, `EventPipeStream`, `SmithyUri` | `io`, `core/serde/event` |
| Codecs | `Codec`, `ShapeSerializer`, `ShapeDeserializer`, `JsonCodec`, `Rpcv2CborCodec`, `XmlCodec` | `codecs/*` |
| Documents | `Document`, `DocumentParser`, `DocumentDeserializer` | `core/serde/document` |
| HTTP | `HttpRequest`, `HttpResponse`, `HttpBinding`, `JavaHttpClientTransport` | `http/*`, `client/client-http*` |
| Auth | `AuthScheme`, `AuthSchemeResolver`, `Signer`, `Identity`, `SigV4AuthScheme` | `auth-api`, `client/client-auth-api`, `aws/aws-sigv4` |
| Client Core | `Client`, `ClientPipeline`, `ClientPlugin`, `ClientInterceptor`, `ClientProtocol`, `ClientTransport` | `client/client-core` |
| Retries/Waiters | `RetryStrategy`, `StandardRetryStrategy`, `AdaptiveRetryStrategy`, `Waiter`, `Matcher` | `retries*`, `client/client-waiters` |
| AWS Protocols | `AwsJson1Protocol`, `RestJsonClientProtocol`, `RestXmlClientProtocol`, `AwsQueryClientProtocol` | `aws/client/*` |
| Dynamic Client | `DynamicClient`, `SchemaConverter`, `StructDocument`, `ContentDocument` | `client/dynamic-client`, `dynamic-schemas` |
| Rules Engine | `RulesEngineBuilder`, `Bytecode`, `BytecodeEvaluator`, `BytecodeEndpointResolver` | `rules-engine` |
| Server | `Service`, `Operation`, `Server`, `ServerProtocol`, `Handler`, `Orchestrator` | `server/*` |
| MCP | `McpServer`, `McpService`, `StdioProxy`, `HttpMcpProxy` | `mcp/*` |
| Code Generation | `JavaCodegenPlugin`, `DirectedJavaCodegen`, `JavaWriter`, `StructureGenerator` | `codegen/*` |
