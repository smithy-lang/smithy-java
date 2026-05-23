# Quarkus Smithy Extension

The Quarkus extension that integrates Smithy-Java codegen and the
Vert.x-mounted server runtime into Quarkus applications. Terminology
here disambiguates concepts that appear under the same English word
inside a single Quarkus JVM.

## Language

### Servers and listeners

**Quarkus HTTP server**:
The Vert.x-based HTTP server provided by `quarkus-vertx-http`. Hosts
the user's Smithy operations (mounted by this extension), plus
`/q/dev`, `/q/health`, REST endpoints, etc. Smithy operations share
this server's port (per ADR-0003).

**Smithy Vert.x server (`SmithyVertxServer`)**:
The upstream module `:server:server-vertx` that this extension
consumes. A `Handler<RoutingContext>` mounted on Quarkus's main
`Router` as a single catch-all route (under
`quarkus.smithy.server.path-prefix` if set; root otherwise). On every
request, runs `ProtocolResolver` over the precision-ordered list of
`ServerProtocol`s the recorder loaded. Requests no protocol claims
fall through via `ctx.next()`; requests a protocol claims-but-rejects
return 404. See ADR-0006 for the public API (`SmithyVertxServer`,
`ServerOptions`) and ADR-0008 for the resolution model.

**Smithy listener (deprecated)**:
The previous architecture, before ADR-0003. Was a separate
`software.amazon.smithy.java.server.Server` (Netty) instance owning
its own listener and port. No longer present in `quarkus-smithy` —
the Vert.x server above mounts on Quarkus's HTTP server instead of
running its own.
_Avoid_: this term referring to anything in the current architecture.

### Programming model

The extension is a **server extension**. The supported user-facing
programming model is the Service-bean model.

**Service-bean model** (supported, the canonical pattern):
The user supplies a CDI producer method that returns a built
`software.amazon.smithy.java.server.Service` (the generated service
stub from `modes: ["server"]` codegen output). The extension
discovers every `@Produces Service` bean and mounts a
`SmithyVertxServer` on Quarkus's main HTTP router from the upstream
`:server:server-vertx` module. There is **no separate Smithy
listener**; operations share the Quarkus HTTP server's port.
_Replaces (per ADR-0003 + ADR-0006)_: "Server-bean model", "@Produces
Server" model.
_Avoid_: "manual server", "explicit server", "two-port mode".

**Annotation-discovery model** (named, deferred):
The hypothetical future programming model in which a CDI annotation
(e.g. `@SmithyService`) marks operation implementations and the
extension builds the `Service` itself, analogous to `quarkus-grpc`'s
`@GrpcService`. Not implemented; called out so that "the model we did
not pick" has a name.

**Typed-client model** (future direction, not shipped):
Generate a Smithy client (`modes: ["client"]`) and expose it as a CDI
bean. The `CodeGenProvider` is mode-agnostic and will emit client
code on demand, but the extension does not surface a documented
runtime path for this pattern. The recorder's no-Service
short-circuit is the only current accommodation.
_Avoid_: treating this as a supported programming model; it is a
forward-looking direction only.

**Types-only model** (future direction, not shipped):
Generate Smithy POJOs (`modes: ["types"]`) and use them as you like.
Same status as Typed-client — codegen runs, no runtime story is
shipped.
_Avoid_: treating this as a supported programming model.

### Smithy concepts (as used inside this extension)

**Smithy `Service`**:
The generated service stub class corresponding to a `service` shape in
the user's `.smithy` model. The user attaches operation implementations
to it via its builder (`CoffeeShop.builder().addCreateOrderOperation(...).build()`).
The extension's recorder discovers `Service` instances via
`Instance<Service>` and hands them to a `SmithyVertxServer`.

**Smithy operation**:
A generated interface for a single RPC, implemented by the user. The
implementation class is referenced by name in the
`<ServiceName>.builder().add<OperationName>Operation(...)` chain inside
the user's `@Produces Service` method.

**`java-codegen` plugin**:
The Smithy build plugin (from `:codegen:codegen-plugin`) that turns
`.smithy` shapes into Java source. The extension only honors this plugin
inside `smithy-build.json`; `smithy-base` Gradle plugin wiring is
intentionally not used. The plugin is **mode-agnostic**: whatever
`modes` (`server`, `client`, `types`) the user puts in
`smithy-build.json` is what's emitted.

## Relationships

- A Quarkus application has zero or more `@Produces Service` beans and
  exactly one Quarkus HTTP server.
- The `SmithyVertxRecorder` runs at `RUNTIME_INIT`, walks
  `Instance<Service>`, and either mounts the `SmithyVertxServer` (one
  or more Service beans) or short-circuits with an INFO log (zero
  beans — e.g. an app that depends on `quarkus-smithy` only for
  codegen).
- The `Annotation-discovery model` is named to mark a boundary, not
  because it exists.

## Example dialogue

> **Dev:** "I have a Smithy service and want it served by my Quarkus
> app. What do I produce?"
> **Domain expert:** "A `@Produces Service` bean. The extension mounts
> every operation on Quarkus's HTTP server automatically — no separate
> port, no `Server.builder()`. See `examples/quarkus-server/`."

> **Dev:** "Can I have two Smithy services in the same Quarkus app?"
> **Domain expert:** "Yes — produce two `@Produces Service` beans. The
> Vert.x server composes them on the same router. `@http` collisions
> across services are resolved by the matcher's tie-break, not at
> bind time (see ADR-0008)."

> **Dev:** "Where's the URL of my Smithy server?"
> **Domain expert:** "There isn't a separate one. Smithy operations
> live on Quarkus's HTTP server — `quarkus.http.host`/`quarkus.http.port`
> control the listener. To put Smithy operations under a sub-tree, set
> `quarkus.smithy.server.path-prefix=/api/smithy`."

## Flagged ambiguities

- "Server" can mean three things in this codebase: (1) the Quarkus
  HTTP server (the listener owning the port); (2) the Smithy Vert.x
  server (`SmithyVertxServer`, a `Handler<RoutingContext>` mounted on
  the Quarkus router); (3) the legacy Smithy Netty listener (no
  longer used in `quarkus-smithy`). When precision matters say
  "Quarkus HTTP server" or "Smithy Vert.x server".
- "Service" can mean a Smithy `service` shape, a Smithy `Service`
  generated stub, or a CDI `@ApplicationScoped` bean. Resolution: we
  use "Service" only for the generated stub (the type returned by the
  user's `@Produces` method); Smithy `service` shape is the model-side
  noun; CDI services are referred to as "beans".
- "@Produces Server" was the user-facing producer pattern in earlier
  experimental releases. ADR-0003 and ADR-0006 superseded it with
  `@Produces Service`. Old references in ADR-0001 are preserved for
  historical accuracy; new prose uses the new name.
