# Dynamic client usage check (Error Prone)

An **[Error Prone](https://errorprone.info) check** that validates `DynamicClient` usage against the Smithy model each
client is *actually built from*, at compile time — **without** any code generation. The dynamic client stays fully
runtime-driven and document-based; this check only reads the source being compiled and catches the mistakes that are
statically catchable.

## What it checks

Given code like:

```java
DynamicClient client = DynamicClient.builder()
        .serviceId(ShapeId.from("smithy.example#Sprockets"))
        .model(Model.assembler().addUnparsedModel("demo.smithy", MODEL).assemble().unwrap())
        .build();

client.call("GetSprokcet", Map.of("id", "1"));  // <-- compile error: typo, no such operation
client.call("GetSprocket", Map.of("idd", "1")); // <-- compile error: 'idd' not an input member
```

the check emits, anchored to the exact argument:

```
error: [DynamicClientUsage] Operation 'GetSprokcet' not found in service 'smithy.example#Sprockets'.
       Known operations: [CreateSprocket, GetSprocket]
    Did you mean 'GetSprocket'?          <-- suggested auto-fix
error: [DynamicClientUsage] 'idd' is not a member of input 'GetSprocketInput' for operation 'GetSprocket'.
       Known members: [id]
```

Concretely, per `call(...)` site whose receiver is a `DynamicClient` it can resolve:

1. **operation name** — the first argument, when a String literal or `static final String` constant, must be an
   operation on the resolved service (with a Levenshtein-based *"did you mean"* suggested fix);
2. **input keys** — when the second argument is a `Map.of("k", v, ...)` literal, every key must be a member of the
   operation's input structure.

## Why Error Prone (vs. a raw javac plugin)

This started as a raw `com.sun.source.util.Plugin`. Porting to Error Prone kept the same idea but replaced hand-rolled
machinery with framework features:

| Hand-rolled in the raw plugin | Provided by Error Prone |
|---|---|
| Custom string-constant folding | `ASTHelpers.constValue(...)` |
| Report-only diagnostics | **`SuggestedFix`** — an applyable "did you mean `GetSprocket`?" auto-fix |
| A `run.sh` shell driver | `CompilationTestHelper` with inline `// BUG: Diagnostic contains:` assertions |
| No suppression | `@SuppressWarnings("DynamicClientUsage")` for free |
| Manual `TaskListener` wiring | `@AutoService(BugChecker.class)` registration |

The **model-resolution core** (`ModelResolver`, `ResolvedClient`, `MapLiteral`) is unchanged from the raw-plugin
prototype and has no dependency on Error Prone or javac internals — it takes a `constantResolver` function, so the same
core could back an Error Prone check, a javac plugin, or a standalone parser task. Error Prone is just the driver.

## How it works

- **Match** (`DynamicClientUsageChecker`) — a `MethodInvocationTreeMatcher` matching `call(...)` on `DynamicClient`.
- **Resolve the client** — walk from the `call` receiver to the local's `DynamicClient.builder()...build()`
  initializer, read the `.model(...)` and `.serviceId(...)` arguments (handling `ShapeId.from("...")`).
- **Resolve + assemble the model** (`ModelResolver`) — statically read the `Model.assembler()...` chain
  (`addUnparsedModel` / `addImport` / `discoverModels`, folding constants) and assemble it with the **real
  `smithy-model` `ModelAssembler`** — the same code the runtime uses, so no drift. Cached per client.
- **Validate** — check the operation name and any `Map.of(...)` input keys, reporting via `buildDescription(...)`.

## The load-bearing rule: abstain, never false-positive

The check only reasons about statically-resolvable values. The instant anything is genuinely dynamic — an operation
name from a field or parameter, a model passed in from elsewhere, `addImport(someUrl)` — it returns
`Description.NO_MATCH` and never errors. `DynamicClient` exists *for* runtime dynamism; a checker that flags valid
dynamic code gets turned off. It catches typos and stays out of the way. (See `abstainsOnDynamicOperationName` and
`abstainsWhenModelNotStaticallyResolvable` in the tests, and the CLI's `SmithyCall`, which is entirely in the abstain
set.)

## Running it in a consumer build

With the `net.ltgt.errorprone` Gradle plugin, put this module on the `errorprone` configuration:

```kotlin
plugins { id("net.ltgt.errorprone") version "..." }
dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
    errorprone(project(":client:dynamic-client-errorprone"))
}
```

### Running *only* this check (no other Error Prone checks)

Consumers who don't want Error Prone's ~500 built-in checks can disable them all and enable just this one:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableAllChecks = true
        error("DynamicClientUsage")
    }
}
```

Note this still requires Error Prone the framework to be present — see "Known limitations".

## Try it

```
./gradlew :client:dynamic-client-errorprone:test
```

The `CompilationTestHelper` tests compile inline sources against the real `DynamicClient` with the check active and
assert the diagnostics (unknown operation, bad input key, auto-detected service, and correct abstention on dynamic op
name and unresolved model).

## Java 25 / toolchain note

Error Prone runs inside `javac` and depends on compiler internals, so it needs the standard `--add-exports` /
`--add-opens` set and `--should-stop=ifError=FLOW`. Verified working on this project's **Java 25** toolchain with
Error Prone **2.50.0**. Because `--add-exports` of a system-module package is incompatible with `--release`, this
module compiles with plain `source`/`target` 21 rather than the `--release 21` the shared conventions set (see
`build.gradle.kts`).

## Known limitations / next steps

- **Requires Error Prone the framework.** "Only this check" disables the *other checks*, but consumers still adopt
  Error Prone + `net.ltgt.errorprone`. If a zero-Error-Prone dependency is required, the raw-javac-plugin form (in git
  history) or a standalone parser-based Gradle task are the alternatives; both reuse the same `ModelResolver` core.
- **Coupled to javac / Error Prone versions.** Newer JDKs can require a matching Error Prone bump.
- **Intra-unit flow only.** A client built in one file and used in another isn't tracked; an optional
  `@SmithyModel(...)` fallback annotation naming the source would extend reach.
- **`discoverModels()` / classpath imports** are approximated via import roots; wiring the compilation's real
  `JavaFileManager` classpath would make it exact.
- Only operation names and `Map.of` input keys are checked today; required members, enum values, and nested document
  shapes are natural extensions.
```
