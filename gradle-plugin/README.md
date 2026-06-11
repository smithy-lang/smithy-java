# Smithy Java Gradle Plugin

A Gradle plugin that simplifies Java code generation from Smithy models. It replaces the manual
boilerplate of applying `smithy-base`, wiring source sets, managing `smithyBuild` dependencies,
and coordinating task ordering with a single plugin application.

## Quick Start

**Before** (manual setup):
```kotlin
plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base") version "1.4.0"
}

dependencies {
    smithyBuild("software.amazon.smithy.java:codegen-plugin:<version>")
    smithyBuild("software.amazon.smithy.java:client-core:<version>")
    api("software.amazon.smithy.java:core:<version>")
    api("software.amazon.smithy.java:framework-errors:<version>")
    api("software.amazon.smithy.java:client-core:<version>")
}

afterEvaluate {
    val path = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets {
        main {
            java { srcDir("$path/java") }
            resources { srcDir("$path/resources") }
        }
    }
}

tasks.compileJava { dependsOn("smithyBuild") }
tasks.processResources { dependsOn("smithyBuild") }
```

**After** (with this plugin):
```kotlin
plugins {
    `java-library`
    id("software.amazon.smithy.java.gradle.smithy-java") version "<version>"
}
```

That's it. The plugin handles `smithy-base`, dependency management, source set wiring, and task
ordering automatically.

## Installation

Add the plugin to your `settings.gradle.kts`:

```kotlin
pluginManagement {
    plugins {
        id("software.amazon.smithy.java.gradle.smithy-java") version "<smithy-java-version>"
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then apply it in your `build.gradle.kts` along with a Java plugin:

```kotlin
plugins {
    `java-library` // or `java` / `application` for leaf projects
    id("software.amazon.smithy.java.gradle.smithy-java")
}
```

## Examples

### Types Only

The simplest case, generate data types from a Smithy model with no client or server runtime.

`smithy-build.json`:
```json
{
  "version": "1.0",
  "plugins": {
    "java-codegen": {
      "namespace": "com.example.types",
      "headerFile": "license.txt",
      "modes": ["types"]
    }
  }
}
```

`build.gradle.kts`:
```kotlin
plugins {
    `java-library`
    id("software.amazon.smithy.java.gradle.smithy-java")
}
```

No additional configuration needed, the plugin adds the correct runtime dependencies automatically.

### Client

Generate a client for a service.

`smithy-build.json`:
```json
{
  "version": "1.0",
  "plugins": {
    "java-codegen": {
      "service": "com.example#MyService",
      "namespace": "com.example.client",
      "headerFile": "license.txt",
      "modes": ["client"]
    }
  }
}
```

`build.gradle.kts`:
```kotlin
plugins {
    `java-library`
    id("software.amazon.smithy.java.gradle.smithy-java")
}

dependencies {
    // Add the protocol and transport your service uses
    implementation("software.amazon.smithy.java:aws-client-restjson:<version>")
}
```

You only need to declare protocol/transport dependencies specific to your service, the plugin
handles the rest.

### Server

Generate server stubs.

`smithy-build.json`:
```json
{
  "version": "1.0",
  "plugins": {
    "java-codegen": {
      "service": "com.example#MyService",
      "namespace": "com.example.server",
      "headerFile": "license.txt",
      "modes": ["server"]
    }
  }
}
```

`build.gradle.kts`:
```kotlin
plugins {
    java
    id("software.amazon.smithy.java.gradle.smithy-java")
}

dependencies {
    // Server runtime and protocol
    implementation("software.amazon.smithy.java:server-netty:<version>")
    implementation("software.amazon.smithy.java:aws-server-restjson:<version>")
}
```

The plugin handles codegen and runtime dependencies automatically, you only need to declare
the server runtime and protocol implementation you want to use.

## Configuration

All configuration is optional. The plugin works out of the box for standard projects.

```kotlin
smithyJava {
    // Explicit mode declaration, overrides smithy-build.json inference.
    // Valid values: "types", "client", "server"
    modes.add("client")

    // Compile output from additional smithy-build.json plugins.
    generatedPluginOutputs.add("trait-codegen")

    // Disable automatic dependency management to control all deps manually.
    autoAddDependencies = false
}
```

### `modes`

Explicitly declares which codegen modes the project uses (`"types"`, `"client"`, `"server"`).
When set, these override whatever is in `smithy-build.json` for dependency resolution purposes.

When empty (default), modes are read from `smithy-build.json` automatically.

### `projections`

When your `smithy-build.json` uses named projections (instead of or in addition to the
default source projection), list them here so the plugin wires each projection's
`java-codegen` output into the main source set:

```kotlin
smithyJava {
    projections.addAll("rest-json-client", "rpc-v2-cbor-client")
}
```

When empty (default), the plugin uses the source projection. Service files from multiple
projections are merged automatically.

### `generatedPluginOutputs`

When your `smithy-build.json` has plugins beyond `java-codegen` that produce Java source
(e.g. `trait-codegen`), list them here so their output gets compiled:

```kotlin
smithyJava {
    generatedPluginOutputs.add("trait-codegen")
}
```

Service files from multiple plugins are merged automatically. Disable with
`mergeServiceFiles = false` if not needed.

### `autoAddDependencies`

When `true` (default), the plugin automatically adds the correct Smithy Java runtime
dependencies based on the active modes. The configuration used depends on which Java plugin
you apply and the codegen mode:

- **`java-library`**: all runtime dependencies are added to `api` unless the mode is
  server-only (no types or client), in which case `implementation` is used.
- **`java` / `application`**: all runtime dependencies are added to `implementation`.

Set to `false` when you need full control over dependency versions or want to use project
dependencies (e.g. in a monorepo):

```kotlin
smithyJava {
    autoAddDependencies = false
}

dependencies {
    smithyBuild("software.amazon.smithy.java:codegen-plugin:<version>")
    api("software.amazon.smithy.java:core:<version>")
    // ... full control
}
```

## Requirements

- Java 21 or later
- Gradle 8.5 or later (first version to support Java 21 runtime)
- A Java plugin applied (`java`, `java-library`, or `application`)
- A `smithy-build.json` with a `java-codegen` plugin configured

## Custom Source Projection

If you use a non-default projection, configure it via the `smithy` extension (provided by
`smithy-base`):

```kotlin
smithy {
    sourceProjection = "custom-projection"
}
```
