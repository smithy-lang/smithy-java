## Example: Quarkus Server

A Smithy-Java service running inside a Quarkus application via the
[`quarkus-smithy` extension](../../quarkus-smithy/README.md). Smithy
operations share Quarkus's HTTP port — no separate Smithy listener.

### Run it

This example is a standalone Gradle build (see "Why a standalone
Gradle build" below). Drive it from the smithy-java root with the
repo's wrapper:

```console
# from smithy-java/ — publishes the smithy-java jars to your local repo
./gradlew publishToMavenLocal

# still from smithy-java/ — runs the example's standalone build
./gradlew --project-dir examples/quarkus-server quarkusDev
```

The server listens on `http://localhost:8080`. Watch the boot log for
the recorder's mount line (`Smithy mounted at /* with N service(s)`)
and the server's own construction line (`Smithy server constructed
with N service(s), M operation(s); protocols (precision order): […]`).

Re-run `publishToMavenLocal` whenever you change smithy-java sources.

### Curl the operations

The CoffeeShop service declares `@restJson1 @rpcv2Cbor @rpcv2Json`, so
each operation is reachable via any of three on-the-wire shapes. The
server picks one per request in protocol-precision order
(rpcv2Cbor → rpcv2Json → restJson1).

#### restJson1 — HTTP routes via `@http(method, uri)`

```console
curl http://localhost:8080/menu
curl -X PUT http://localhost:8080/order -H 'Content-Type: application/json' \
     -d '{"coffeeType":"LATTE"}'
curl http://localhost:8080/order/<id-from-PUT-response>
```

#### rpcv2Cbor — `/service/CoffeeShop/operation/<Op>` + `smithy-protocol` header

```console
# GetMenu — empty input is the CBOR empty map (0xa0)
printf '\xa0' > /tmp/empty.cbor
curl -X POST http://localhost:8080/service/CoffeeShop/operation/GetMenu \
     -H 'smithy-protocol: rpc-v2-cbor' -H 'content-type: application/cbor' \
     --data-binary @/tmp/empty.cbor

# CreateOrder — {"coffeeType":"LATTE"}
python3 -c 'import sys; sys.stdout.buffer.write(bytes([0xa1, 0x6a]) + b"coffeeType" + bytes([0x65]) + b"LATTE")' \
     > /tmp/createorder.cbor
curl -X POST http://localhost:8080/service/CoffeeShop/operation/CreateOrder \
     -H 'smithy-protocol: rpc-v2-cbor' -H 'content-type: application/cbor' \
     --data-binary @/tmp/createorder.cbor
```

The response body is CBOR; pipe through `xxd` or a CBOR diagnostic tool
to read it.

#### rpcv2Json — same URI scheme, JSON body

```console
curl -X POST http://localhost:8080/service/CoffeeShop/operation/GetMenu \
     -H 'smithy-protocol: rpc-v2-json' -H 'content-type: application/json' -d '{}'

curl -X POST http://localhost:8080/service/CoffeeShop/operation/CreateOrder \
     -H 'smithy-protocol: rpc-v2-json' -H 'content-type: application/json' \
     -d '{"coffeeType":"ESPRESSO"}'

curl -X POST http://localhost:8080/service/CoffeeShop/operation/GetOrder \
     -H 'smithy-protocol: rpc-v2-json' -H 'content-type: application/json' \
     -d '{"id":"<id-from-CreateOrder>"}'
```

#### Fall-through behavior

The server distinguishes three outcomes per request, observable as
distinct 404 shapes:

```console
# no-claim → ctx.next() → Quarkus default 404 (text/plain ~358B page)
curl -i -X POST http://localhost:8080/service/CoffeeShop/operation/GetMenu

# claim-and-reject → server 404 with empty body
curl -i -X POST http://localhost:8080/service/CoffeeShop/operation/ \
     -H 'smithy-protocol: rpc-v2-cbor' -H 'content-type: application/cbor' \
     --data-binary @/tmp/empty.cbor

# unrelated path → ctx.next() → Quarkus (or your own sibling handler)
curl -i http://localhost:8080/q/notreal
```

The empty-body 404 (claim-and-reject) vs the Quarkus default-page 404
(no-claim) is the observable signal for routing correctness. A request
that *claimed* the rpcv2-cbor protocol but failed URI parsing is
intercepted before `ctx.next()`, so a sibling handler can't
misinterpret it. A request that no protocol claimed falls through, so
Quarkus (or any other Vert.x handler on the same router) gets a chance
to serve it.

### Hot reload

While `quarkusDev` is running:

- Edit `CreateOrder.java` (e.g., change a status string), save → re-curl
  `PUT /order`. The response reflects the change without a restart.
- Edit `src/main/smithy/coffee.smithy` (e.g., add a member), save → the
  `CodeGenProvider` regenerates the stub and the recorder removes the
  previous Vert.x route before installing the new one.

### Path-prefix mode

To put Smithy operations under `/api/smithy/...` (so REST endpoints can
own the root), set in `src/main/resources/application.properties`:

```properties
quarkus.smithy.server.path-prefix=/api/smithy
```

`@http(uri:"/menu")` then becomes reachable at `/api/smithy/menu`. Verify:

```console
curl -i http://localhost:8080/api/smithy/menu   # 200
curl -i http://localhost:8080/menu              # 404
```

In dev mode you can also live-edit this from the Dev UI Configuration
tile — see below.

### Packaged jar (prod profile)

```console
# from smithy-java/
./gradlew --project-dir examples/quarkus-server quarkusBuild
java -jar examples/quarkus-server/build/quarkus-app/quarkus-run.jar
```

Run the same curl probes against this — they should all 200, boot is
sub-2s.

### Dev UI

While `quarkusDev` is running, open `http://localhost:8080/q/dev-ui`.

There is no Smithy-specific Dev UI card today (none of the extension's
build steps emit a `CardPageBuildItem`), so use the standard tiles:

- **Endpoints** — confirms the Smithy server's catch-all route
  alongside Quarkus's own routes.
- **Configuration** — search for `quarkus.smithy.server` to live-edit
  `path-prefix`, `workers`, and `shutdown-grace`.
- **ArC** — confirms the `@Produces Service` bean is present and
  unremovable (the extension marks it via `UnremovableBeanBuildItem`).
- **Build Steps** — confirms `SmithyProcessor` ran and which build
  items it produced.
- **Continuous Testing** — press `r` in the dev terminal (or open the
  tile) to re-run tests on save.

---

### How it's wired

The user produces a `@Produces Service` bean (the generated `CoffeeShop`
stub) and the extension mounts a `SmithyVertxServer` from the upstream
`:server:server-vertx` module on Quarkus's main HTTP router:

```java
@ApplicationScoped
public class CoffeeShopServerConfig {

    @Produces
    @Singleton
    Service coffeeShop() {
        return CoffeeShop.builder()
                .addCreateOrderOperation(new CreateOrder())
                .addGetMenuOperation(new GetMenu())
                .addGetOrderOperation(new GetOrder())
                .build();
    }
}
```

#### Project layout

```
.
├── build.gradle.kts                  ← apply io.quarkus, depend on quarkus-smithy
├── settings.gradle.kts
├── gradle.properties
├── smithy-build.json                 ← project root, configures java-codegen
├── src/main/smithy/                  ← .smithy models (Quarkus convention)
│   ├── coffee.smithy
│   ├── main.smithy
│   └── order.smithy
├── src/main/java/.../CoffeeShopServerConfig.java
├── src/main/java/.../CreateOrder.java
├── src/main/java/.../GetMenu.java
├── src/main/java/.../GetOrder.java
└── src/main/resources/application.properties
```

No `afterEvaluate { ... srcDir(...) }` wiring. No
`compileJava.dependsOn(smithyBuild)`. The `quarkus-smithy` extension's
`CodeGenProvider` runs as part of `quarkusGenerateCode`, generates Java
sources directly into Quarkus's
`build/classes/java/quarkus-generated-sources/smithy/` output directory,
and `compileJava` picks them up automatically.

#### Why a standalone Gradle build

This example is intentionally not included in `smithy-java`'s root
`settings.gradle.kts`. Quarkus dev-mode workspace discovery would
otherwise substitute sibling smithy-java projects' raw `build/classes`
directories for their published jars — bypassing
`:codecs:json-codec`'s shadowJar (which relocates Jackson 3) and
splitting the classloader graph in ways that break the
`SchemaExtensionProvider` SPI lookup. Running standalone, against the
locally-published jars, makes the example behave exactly the way a
real customer's project would.

### Running the extension's tests

These live in the parent smithy-java build, not in this example:

```console
# from smithy-java/
./gradlew :server:server-vertx:test                 # Smithy Vert.x server tests
./gradlew :quarkus-smithy-integration-tests:test    # extension integ
./gradlew :aws:server:aws-server-restjson:integ     # protocol integ
```
