# Code Generation

> **Last updated:** April 29, 2026

Smithy-Java has a single codegen plugin that generates Java code from Smithy models. It can be configured for one or
more modes, `client`, `server`, and `types`, and produces generated types, client implementations, and server stubs
from the same codebase. The codegen builds on the
[smithy-codegen-core](https://github.com/smithy-lang/smithy/tree/main/smithy-codegen-core) framework and follows the
[directed codegen pattern](https://smithy.io/2.0/guides/building-codegen/index.html).

**Source:** [`codegen/`](https://github.com/smithy-lang/smithy-java/tree/main/codegen)

## Module Structure

```
codegen/
├── codegen-core/        # Core framework: generators, writer, integrations, symbol provider
│   ├── src/main/        # Public API shared by all modes
│   └── src/internal/    # JavaTypesCodegenPlugin, TypesDirectedJavaCodegen
├── codegen-plugin/      # Main plugin: JavaCodegenPlugin, DirectedJavaCodegen, client/server generators
├── plugins/             # Thin wrapper JARs (client-codegen, server-codegen, types-codegen)
└── test-utils/          # Test utilities
```

The split between `codegen-core` and `codegen-plugin` is intentional: `codegen-core` contains everything needed for type
generation (shared between client and server), while `codegen-plugin` adds client-specific and server-specific
generators. This allows lightweight type-only generation without pulling in client/server dependencies.

## Plugin Architecture

### SmithyBuildPlugin Implementations

Two plugins are registered via Java SPI (`META-INF/services/software.amazon.smithy.build.SmithyBuildPlugin`):

1. **`JavaCodegenPlugin`** (`codegen-plugin`) — name: `"java-codegen"`. The primary plugin supporting all modes.
2. **`JavaTypesCodegenPlugin`** (`codegen-core/internal`) — name: `"internal-types-only"`. A lightweight types-only
   plugin used internally (e.g., by `framework-errors`). Lives in `codegen-core` to avoid pulling in client/server
   dependencies.

### Configuration

The plugin is configured via `smithy-build.json`:

```json
{
  "plugins": {
    "java-codegen": {
      "service": "com.example#CoffeeShop",
      "namespace": "com.example",
      "modes": ["client"],
      "headerFile": "license.txt",
      "addNullnessAnnotations": true,
      "protocol": "smithy.protocols#rpcv2Cbor",
      "edition": "LATEST",
      "runtimeTraits": ["custom.trait#MyTrait"],
      "runtimeTraitsSelector": "[id|namespace = 'custom']"
    }
  }
}
```

Key settings:
- `service` — The Smithy service shape ID to generate for (required for client/server modes).
- `namespace` — Java package namespace for generated code.
- `modes` — Array of `"client"`, `"server"`, and/or `"types"`. Determines what gets generated.
- `protocol` — Optional protocol to target. Affects which traits are included in schemas.
- `runtimeTraits` / `runtimeTraitsSelector` — Additional traits to include in generated schemas beyond the defaults.

### Execution Flow

`JavaCodegenPlugin.execute(PluginContext)`:

1. Parses `modes` from settings
2. Routes to either `executeTypesMode()` or `executeServiceMode()`:
   ```java
   if (modes.contains(TYPES) && !modes.contains(CLIENT) && !modes.contains(SERVER)) {
       executeTypesMode(context, settingsNode, modes);
   } else {
       executeServiceMode(context, settingsNode, modes);
   }
   ```
3. Creates a `CodegenDirector` parameterized with `<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings>`
4. Applies `DefaultTransforms` (enum conversion, pagination flattening, idempotency tokens, deprecation filtering, dedicated I/O, etc.)
5. Calls `runner.run()` which drives the directed codegen pattern

### Dependency Validation

`JavaCodegenPlugin.validateDependencies()` checks at runtime that required classes are on the classpath:
- CLIENT mode → requires `software.amazon.smithy.java.client.core.Client`
- SERVER mode → requires `software.amazon.smithy.java.server.Service`
- CLIENT with endpoint rules → requires `RulesEngineBuilder`
- CLIENT with waiters → requires `Waiter`

## Modes

`CodegenMode` is a simple enum: `CLIENT`, `SERVER`, `TYPES`.

| Mode(s) | Service Required | Types Generated | Client Generated | Server Generated |
|---------|-----------------|-----------------|-----------------|-----------------|
| `[types]` | No (synthetic) | ✅ | ❌ | ❌ |
| `[client]` | Yes | ✅ | ✅ | ❌ |
| `[server]` | Yes | ✅ | ❌ | ✅ |
| `[client, server]` | Yes | ✅ | ✅ | ✅ |
| `[client, types]` | Yes | ✅ (expanded) | ✅ | ❌ |

When TYPES is combined with CLIENT or SERVER, `expandServiceClosureForTypes()` adds all model shapes (structures, unions, enums, intEnums) not already in the service closure via a synthetic operation.

## The Directed Codegen Pattern

The Smithy codegen framework uses a "directed codegen" pattern where the framework walks the model and calls
shape-specific methods on a `DirectedCodegen` implementation. This is documented in the
[Smithy codegen guide](https://smithy.io/2.0/guides/building-codegen/index.html).

### DirectedJavaCodegen

[`DirectedJavaCodegen`](https://github.com/smithy-lang/smithy-java/blob/main/codegen/codegen-plugin/src/main/java/software/amazon/smithy/java/codegen/DirectedJavaCodegen.java)
is the unified implementation for all modes. Shape generation methods are unconditional, they always run. Only
`generateOperation()` and `generateService()` have mode-conditional branches:

```java
void generateStructure(...)  → StructureGenerator     // always
void generateError(...)      → StructureGenerator     // always
void generateUnion(...)      → UnionGenerator          // always
void generateEnumShape(...)  → EnumGenerator           // always

void generateOperation(...) {
    if (isSynthetic || isTypesOnly) return;             // skip for types-only
    if (modes.contains(SERVER)) new OperationInterfaceGenerator().accept(directive);
    new OperationGenerator().accept(directive);
}

void generateService(...) {
    if (isTypesOnly) return;
    if (modes.contains(CLIENT)) {
        new ClientInterfaceGenerator().accept(directive);
        new ClientImplementationGenerator().accept(directive);
    }
    if (modes.contains(SERVER)) new ServiceGenerator().accept(directive);
    new ApiServiceGenerator().accept(directive);
    new ServiceExceptionGenerator<>().accept(directive);
}
```

There's also a `TypesDirectedJavaCodegen` in `codegen-core/internal` — a stripped-down version where
`generateOperation()` and `generateService()` are no-ops. Used only by `JavaTypesCodegenPlugin`.

### Synthetic Shape Filtering

Both implementations skip shapes in the `smithy.synthetic` namespace:

```java
private static boolean isSynthetic(Shape shape) {
    return shape.getId().getNamespace().equals(SyntheticServiceTransform.SYNTHETIC_NAMESPACE);
}
```

## The Dummy Service Trick for Types Codegen

**Problem**: Directed codegen requires a root `ServiceShape` to walk the model. Types-only mode has no service.

**Solution**:
  [`SyntheticServiceTransform`](https://github.com/smithy-lang/smithy-java/blob/main/codegen/codegen-core/src/main/java/software/amazon/smithy/java/codegen/SyntheticServiceTransform.java)
  creates a synthetic service that wraps the target shapes.

This is likely to change when we finish the design and implementation of types codegen that can be used for all Smithy
based SDKs.

### How It Works

`SyntheticServiceTransform.transform(Model, Set<Shape>, Map<ShapeId, String>)`:

1. Creates a `ServiceShape` with ID `smithy.synthetic#TypesGenService` and a `SyntheticTrait`
2. For each shape in the closure:
   - **Operations** → added directly to the service
   - **Structures, Enums, IntEnums, Unions** → collected for wrapping
3. Creates synthetic wrapper shapes:
   - `smithy.synthetic#TypesOperationInput` — a structure with one member per type (`m0`, `m1`, ...)
   - `smithy.synthetic#TypesOperationOutput` — empty structure
   - `smithy.synthetic#TypesOperation` — operation with `@private` and `@synthetic` traits
4. Adds the synthetic operation to the service
5. Returns the modified model

When TYPES is combined with CLIENT/SERVER, `expandServiceClosure()` adds a synthetic operation to the *existing* service to include additional shapes not already in its closure.

### TypeCodegenSettings

For types-only mode, `TypeCodegenSettings` wraps `JavaCodegenSettings` and injects the synthetic service ID:

```java
nodeBuilder.withMember("service", SyntheticServiceTransform.SYNTHETIC_SERVICE_ID.toString());
return JavaCodegenSettings.fromNode(nodeBuilder.build());
```

Default selector: `:is(structure, union, enum, intEnum)`, generates all aggregate types.

## JavaWriter and Format Patterns

### Class Hierarchy

```
SymbolWriter<W, I>                    (smithy-codegen-core)
  └── DeferredSymbolWriter<W, I>      (codegen-core) — adds symbol table for deferred name resolution
        └── JavaWriter                (codegen-core) — Java-specific formatters and import handling
```

`DeferredSymbolWriter` adds a `Map<String, Set<Symbol>> symbolTable` that tracks all symbols by name. This enables detecting name conflicts (e.g., two classes named `Error` from different packages) and resolving them at `toString()` time by using fully-qualified names.

### Format Patterns

JavaWriter registers custom formatters that are used extensively in code templates:

| Formatter | Character | Purpose | Example |
|-----------|-----------|---------|---------|
| `$T` | T | Java type reference | `$T` → `MyClass` (with import) |
| `$B` | B | Boxed type | `int` → `Integer` |
| `$N` | N | Nullable annotation | Adds `@Nullable` when enabled, else falls back to `$B` |
| `$U` | U | Capitalize first letter | `"foo"` → `"Foo"` |
| `$L` | L | Literal (inherited) | Writes value as-is |
| `$S` | S | String literal (inherited) | Writes value quoted |
| `$C` | C | Code section/consumer (inherited) | Executes a `Runnable` or `Consumer<JavaWriter>` |

### The $C Formatter

`$C` is the most important formatter for understanding the codegen. It executes a `Runnable` or `Consumer<JavaWriter>`
inline. This is how large templates are broken into manageable pieces, as documented in the
[Smithy codegen guide](https://smithy.io/2.0/guides/building-codegen/generating-code.html#breaking-up-large-templates-with-the-c-formatter).

Generators store sub-generators in the writer's context:

```java
writer.putContext("schemas", new SchemaFieldGenerator(directive, writer, shape));
writer.putContext("properties", new PropertyGenerator(directive, writer));
writer.putContext("constructor", new ConstructorGenerator(directive, writer));
```

Then the template references them:

```java
writer.write("""
    public final class ${shape:T} {
        ${schemas:C|}
        ${properties:C|}
        ${constructor:C|}
    }
    """);
```

The `${name:C|}` syntax means "execute the Runnable stored in context key 'name', with a trailing separator".

### The $T Formatter

`$T` handles Java type references with automatic import management. For generic types, it recursively formats type
references:

```java
writer.write("$T<$T, $T>", mapSymbol, keySymbol, valueSymbol);
// → Map<String, Integer> (with imports for Map, String, Integer)
```

The placeholder mechanism uses `£{fullName:L}` internally, which is resolved at `toString()` time after all symbols are
collected and conflicts detected.

### toString() Output

```java
public String toString() {
    putNameContext();  // Resolve all symbol name conflicts
    setExpressionStart('£');  // Switch to £ for final resolution
    return format("£Lpackage £L;\n\n£L\n£L", header, packageNamespace, imports, body);
}
```

## Type Generation

### How Types Are Shared Between Client and Server

All type generators live in `codegen-core` and are shared:
- `StructureGenerator`, `EnumGenerator`, `UnionGenerator`, `ListGenerator`, `MapGenerator`
- `SchemasGenerator`, `SharedSerdeGenerator`, `SchemaIndexGenerator`
- `OperationGenerator` (generates `ApiOperation` classes)
- `BuilderGenerator`, `SchemaFieldGenerator`, `SerializerMemberGenerator`, etc.

Mode-specific generators live in `codegen-plugin`:
- Client: `ClientInterfaceGenerator`, `ClientImplementationGenerator`, `BddFileGenerator`, `WaiterContainerGenerator`
- Server: `ServiceGenerator`, `OperationInterfaceGenerator`

### StructureGenerator

Generates a `public final class` implementing `SerializableStruct` (or extending `ModeledException` for errors). Uses a template with context-driven sub-generators:

| Context Key | Generator | Purpose |
|-------------|-----------|---------|
| `schemas` | `SchemaFieldGenerator` | `$SCHEMA` static field |
| `properties` | `PropertyGenerator` | Private final fields |
| `constructor` | `ConstructorGenerator` | Private constructor from Builder |
| `getters` | `GetterGenerator` | Public getter methods |
| `equals` | `EqualsGenerator` | `equals()` with cost-optimized member ordering |
| `hashCode` | `HashCodeGenerator` | `hashCode()` |
| `serializer` | `StructureSerializerGenerator` | `serializeMembers()` |
| `builder` | `StructureBuilderGenerator` | Nested `Builder` class |

Key behaviors:
- Error structures extend `ModeledException` (or a service-specific exception)
- Streaming members make the class implement `Closeable`
- Collections are wrapped with `Collections.unmodifiable*()` in the constructor
- `EqualsGenerator` uses `CheapEqualsFirstComparator` to order equality checks by cost (primitives first)
- Builder has `PresenceTracker` for required members and `errorCorrection()` for client error correction

### EnumGenerator

Generates a `public sealed interface` implementing `SmithyEnum` (string) or `SmithyIntEnum` (int):

- Static instances for each variant (e.g., `MyEnum OPTION_ONE = new OptionOneType();`)
- Inner `final class` for each known variant
- `$Unknown` record for unknown values
- Builder with `value()` setter and switch-based `build()`

### UnionGenerator

Generates a `public sealed interface` implementing `SerializableStruct`:

- Inner `record` for each variant (e.g., `record FooMember(String foo) implements MyUnion`)
- `$Unknown` record for unknown members
- Builder enforces single-value constraint

### SchemasGenerator

Runs during `customizeBeforeIntegrations()`. Generates `Schemas` classes containing static `Schema` fields for all
shapes. Uses `SchemaFieldOrder` to partition shapes across multiple classes to avoid JVM class file size limits.

Recursive shapes use a two-phase approach in the generated code:

```java
// Phase 1: Builder
static final SchemaBuilder FOO_BUILDER = Schema.structureBuilder(id);

// Phase 2: Members (in static block)
FOO_BUILDER.putMember("bar", BAR_SCHEMA);
FOO_BUILDER.putMember("self", FOO_BUILDER); // recursive

// Phase 3: Build
static final Schema FOO = FOO_BUILDER.build().resolve();
```

## Integration System

### JavaCodegenIntegration

```java
public interface JavaCodegenIntegration
    extends SmithyIntegration<JavaCodegenSettings, JavaWriter, CodeGenerationContext> {

    default List<TraitInitializer<? extends Trait>> traitInitializers() {
        return List.of();
    }
}
```

Integrations are discovered via SPI
(`META-INF/services/software.amazon.smithy.java.codegen.JavaCodegenIntegration`). They can:

- `preprocessModel()` — Transform the model before codegen
- `decorateSymbolProvider()` — Customize symbol resolution
- `interceptors()` — Add code section interceptors
- `customize()` — Post-generation customization
- `traitInitializers()` — Register trait construction code generators

### Built-in Integrations

**`CoreIntegration`** (priority -1, runs last):
- Decorates `SymbolProvider` to track generated symbols per package
- Registers 15 `TraitInitializer` implementations for prelude traits (pagination, HTTP, compression, length, range,
  etc.)
- Includes a `GenericTraitInitializer` catch-all that uses `TraitService` SPI + Node deserialization for any trait
  without a specialized initializer

**`JavadocIntegration`**:
- Registers 9 code interceptors for Javadoc generation: `@SmithyGenerated` annotation, documentation text, `@see` links,
  `@since` tags, `@Deprecated` annotation, operation error docs, builder setter docs, etc.

### Code Sections

Interceptors target specific `CodeSection` types. When the codegen writes a section (e.g., a class declaration, a getter
method), registered interceptors can inject additional code before/after:

- `ClassSection` — wraps class/interface generation
- `GetterSection` — wraps getter methods
- `BuilderSetterSection` — wraps builder setter methods
- `OperationSection` — wraps operation methods
- `JavadocSection` — wraps Javadoc blocks
- `MemberSerializerSection` / `MemberDeserializerSection` — wraps serde code

### TraitInitializer

```java
public interface TraitInitializer<T extends Trait> extends BiConsumer<JavaWriter, T> {
    Class<T> traitClass();
}
```

Used by `TraitInitializerGenerator` to write trait construction code in Schema definitions. For example,
`LengthTraitInitializer` writes `new LengthTrait(min, max)` in the generated `Schemas` class.

## JavaSymbolProvider

[`JavaSymbolProvider`](https://github.com/smithy-lang/smithy-java/blob/main/codegen/codegen-core/src/main/java/software/amazon/smithy/java/codegen/JavaSymbolProvider.java)
maps Smithy shapes to Java `Symbol` objects. It's mode-aware:

- **Structures** → `{namespace}.model.{Name}`
- **Enums/IntEnums** → `{namespace}.model.{Name}`
- **Operations** → `{namespace}.model.{Name}` (with server properties when SERVER mode)
- **Services** → mode-dependent:
  - CLIENT → `{namespace}.client.{Name}Client`
  - SERVER → `{namespace}.service.{Name}`
  - TYPES → `null`

Key symbol properties:
- `IS_PRIMITIVE` — whether the type is a Java primitive
- `BOXED_TYPE` — boxed version (e.g., `int` → `Integer`)
- `EXTERNAL_TYPE` — marks externally-defined types to skip generation
- `SERVICE_EXCEPTION` — service-specific exception symbol

## CodeGenerationContext

Central context object available throughout codegen:

```java
public class CodeGenerationContext {
    Model model();
    JavaCodegenSettings settings();
    SymbolProvider symbolProvider();
    FileManifest fileManifest();
    WriterDelegator<JavaWriter> writerDelegator();
    List<JavaCodegenIntegration> integrations();
    Set<ShapeId> runtimeTraits();          // Traits to include in Schema definitions
    SchemaFieldOrder schemaFieldOrder();    // Ordering/partitioning for Schema classes
    TraitInitializer<T> getInitializer(T); // Find initializer for a trait
}
```

### Runtime Traits Collection

`collectRuntimeTraits()` builds the set of traits to include in generated Schemas:
1. Static prelude traits (~25 traits: LengthTrait, PatternTrait, RequiredTrait, etc.)
2. Protocol-defined traits (from `ProtocolDefinitionTrait.getTraits()`)
3. Auth-defined traits (from `AuthDefinitionTrait.getTraits()`)
4. User-configured traits (`runtimeTraits` and `runtimeTraitsSelector` settings)

## Key Classes Summary

| Class | Location | Role |
|-------|----------|------|
| `JavaCodegenPlugin` | codegen-plugin | Main SmithyBuildPlugin, routes to types/service mode |
| `JavaTypesCodegenPlugin` | codegen-core/internal | Lightweight types-only plugin |
| `DirectedJavaCodegen` | codegen-plugin | Unified DirectedCodegen for all modes |
| `TypesDirectedJavaCodegen` | codegen-core/internal | Types-only DirectedCodegen |
| `CodegenMode` | codegen-core | Enum: CLIENT, SERVER, TYPES |
| `JavaCodegenSettings` | codegen-core | Plugin settings (service, namespace, protocol, etc.) |
| `JavaWriter` | codegen-core | Code writer with $T, $B, $N, $U formatters |
| `DeferredSymbolWriter` | codegen-core | Base class with deferred symbol name resolution |
| `JavaSymbolProvider` | codegen-core | Maps Smithy shapes to Java Symbols |
| `JavaCodegenIntegration` | codegen-core | SPI for extending codegen |
| `CoreIntegration` | codegen-core | Registers trait initializers, decorates symbol provider |
| `SyntheticServiceTransform` | codegen-core | Creates synthetic service for types-only mode |
| `StructureGenerator` | codegen-core | Generates structure/error classes |
| `EnumGenerator` | codegen-core | Generates sealed interface enums |
| `UnionGenerator` | codegen-core | Generates sealed interface unions |
| `SchemasGenerator` | codegen-core | Generates Schema static fields |
| `OperationGenerator` | codegen-core | Generates ApiOperation classes |
| `TraitInitializer` | codegen-core | Interface for writing trait construction code |
| `ClientInterfaceGenerator` | codegen-plugin | Generates client interface |
| `ClientImplementationGenerator` | codegen-plugin | Generates client implementation |
| `ServiceGenerator` | codegen-plugin | Generates server Service class |
| `OperationInterfaceGenerator` | codegen-plugin | Generates server operation interfaces |
| `CodeGenerationContext` | codegen-core | Central context with model, symbols, traits, integrations |
