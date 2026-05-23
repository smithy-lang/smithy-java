# quarkus-smithy

A Quarkus extension that integrates the Smithy-Java server runtime —
codegen plus mounting — into Quarkus applications. Users produce
`Service` beans; the extension mounts every operation on Quarkus's
HTTP router.

> **Status:** experimental. APIs and configuration keys may change without
> notice between releases. Do not use in production.

## What it does

1. **Codegen.** Replaces the standard `smithy-base` Gradle plugin path.
   When `quarkusGenerateCode` runs (as part of `compileJava`,
   `quarkusBuild`, or `quarkusDev`), the extension's `CodeGenProvider`
   discovers `smithy-build.json`, runs `SmithyBuild` with the
   `java-codegen` plugin in-process, and lays generated Java sources
   into `build/classes/java/quarkus-generated-sources/smithy/` where
   Quarkus's compileJava picks them up automatically.

2. **Runtime mounting.** Discovers every CDI bean of type
   `software.amazon.smithy.java.server.Service` (typically produced by
   the user via `@Produces`) and mounts a `SmithyVertxServer` from the
   upstream [`:server:server-vertx`](../server/server-vertx/) module
   on Quarkus's main HTTP router as a single catch-all route. There is
   **no separate Smithy listener**: operations share the Quarkus HTTP
   server's port (per ADR-0003) and are reachable at their
   protocol-defined paths (`@http(method, uri)` for restJson1; computed
   `POST /service/<Name>/operation/<Op>` for rpcv2).

## Programming model

`quarkus-smithy` is a **server extension**. The supported user shape is
the **Service-bean model**: declare a CDI producer that returns a built
Smithy `Service`, and the extension mounts every operation on Quarkus's
HTTP server.

`smithy-build.json`:

```json
{
  "version": "1.0",
  "plugins": {
    "java-codegen": {
      "service": "com.example#CoffeeShop",
      "namespace": "com.example",
      "protocol": "aws.protocols#restJson1",
      "modes": ["server"]
    }
  }
}
```

User code:

```java
@ApplicationScoped
class CoffeeShopServerConfig {
    @Produces @Singleton
    Service coffeeShop() {
        return CoffeeShop.builder()
                .addCreateOrderOperation(new CreateOrder())
                .addGetMenuOperation(new GetMenu())
                .addGetOrderOperation(new GetOrder())
                .build();
    }
}
```

`application.properties`:

```properties
quarkus.http.port=8080
```

That's it. No `Server.builder().endpoints(...)`. No URL strings. The
extension mounts every operation in `CoffeeShop` on the Quarkus HTTP
server. Operations declared with `@http(method, uri)` are reachable at
that path; rpcv2 operations are reachable at
`/service/<Name>/operation/<Op>`.

The canonical worked example, with end-to-end Dev & Test guide, lives
at [`examples/quarkus-server`](../examples/quarkus-server/).

See [`CONTEXT.md`](./CONTEXT.md) for the full glossary.

## Configuration

| Key                                  | Default | Notes                                                                   |
| ------------------------------------ | ------- | ----------------------------------------------------------------------- |
| `quarkus.http.host` / `port`         | (Quarkus defaults) | Smithy operations share the Quarkus HTTP server.                |
| `quarkus.smithy.server.path-prefix`  | (none)  | Prepended to every Smithy operation's route.                            |
| `quarkus.smithy.server.workers`      | `procs * 2` | Worker pool size for the orchestrator group.                       |
| `quarkus.smithy.server.shutdown-grace` | `10s`   | Bound applied by the recorder on `SmithyVertxServer.shutdown()` (best-effort). |

The extension does not configure listener-level concerns (host, port,
TLS, HTTP/2). Use the standard `quarkus.http.*` keys for those — the
Smithy server inherits whatever the Quarkus HTTP server speaks.

### Mounting under a sub-tree

To put Smithy operations under `/api/smithy/...` (so REST endpoints can
own the root):

```properties
quarkus.smithy.server.path-prefix=/api/smithy
```

`@http(uri:"/menu")` is then reachable at `/api/smithy/menu`.

## Modules

- `runtime/` — runtime classpath, ships with the application. Contains
  the `SmithyVertxRecorder` and `SmithyServerConfig`. Depends on
  `:server:server-api` and `:server:server-vertx`.
- `deployment/` — deployment classpath, build-time only. Contains
  `SmithyCodeGenProvider` and `SmithyProcessor` (the Quarkus
  `@BuildStep`s). Bundles `software.amazon.smithy:smithy-build` and
  `software.amazon.smithy.java:codegen-plugin` so the user does not
  need to declare them.
- `integration-tests/` — exercises `SmithyCodeGenProvider` end-to-end
  across the codegen modes the underlying plugin supports.

## Why "experimental"

- Depends on `JavaCodegenPlugin`'s settings shape, which is
  `@SmithyInternalApi`. A smithy-java release could break this
  extension without notice.
- Only the `source` Smithy projection is run.
- Only the `java-codegen` plugin block is honored.
- Native-image support is out of scope for this cut.
- `@streaming` blob operations are unsupported in this release; the
  recorder installs Vert.x's `BodyHandler` upstream of the Smithy
  server so request bodies are fully buffered before resolution.
  Tracked as open question 6 in ADR-0006.

## Future directions

The `CodeGenProvider` is intentionally mode-agnostic — it runs whatever
`modes` the user puts in `smithy-build.json`. That leaves room for
programming models the extension does not currently surface as
supported user-facing shapes:

- **Typed-client.** Generate a Smithy client (`modes: ["client"]`) and
  expose it as a CDI bean. Today the runtime side has no extension
  support beyond codegen — the recorder's no-Service short-circuit is
  the only accommodation.
- **Types-only.** Generate Smithy POJOs (`modes: ["types"]`) and use
  them however you like (REST resources, Vert.x routes, internal DTOs)
  with Smithy's JSON codec. No runtime mounting, just codegen.

These are not shipped as documented programming models in this release.
If demand emerges, they can be promoted in a future ADR with their own
examples and integration tests.

## Design rationale

See `/docs/adr/` at the workspace root —
[0001](../../docs/adr/0001-produces-server-not-annotation-discovery.md),
[0002](../../docs/adr/0002-bundle-codegen-in-deployment-artifact.md),
[0003](../../docs/adr/0003-adopt-shared-transport-and-interceptor-spi.md),
[0004](../../docs/adr/0004-limit-deployment-bundling-scope.md),
[0006](../../docs/adr/0006-service-bean-model-for-shared-transport.md).
