# <img alt="Smithy" src="https://github.com/smithy-lang/smithy/blob/main/docs/_static/smithy-anvil.svg?raw=true" width="32"> Smithy Java 
[![ci](https://github.com/smithy-lang/smithy-java/actions/workflows/ci.yml/badge.svg)](https://github.com/smithy-lang/smithy-java/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[Smithy](https://smithy.io/2.0/index.html) code generators for clients, servers, and shapes for [Java](https://java.com/).

> [!WARNING]
> This project is still in development and is not intended for use in production.

---
This repository contains two major components:
1. Smithy code generators for Java
2. Core modules and interfaces for building services and clients in Java

These components facilitate generating clients, servers stubs, and types for a Smithy service. 
The [codegen](./codegen) directory contains the source code for generating clients.

> [!NOTE] 
> This repository does not contain any generated clients, such as for S3 or other AWS services. 
> Rather, these are the tools that facilitate the generation of those clients and non-AWS Smithy clients.

## Getting Started 
If this is your first time using Smithy, follow the [Smithy Quickstart guide](https://smithy.io/2.0/quickstart.html) 
to learn the basics and create a simple Smithy model.

For a guided tour of this project, see the [Smithy Java Quickstart guide](https://smithy.io/2.0/java/quickstart.html).

The [examples](./examples) are standalone projects that showcase the usage of Smithy Java for generating clients 
and building services. These examples can be used as a template for a new project using the 
[Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html) `init` command.

## Usage

> [!WARNING]
> Smithy-Java only supports **Java 17** or later. Older Java versions are not supported.

### Codegen (Gradle)
To use the Smithy Java code generators with Gradle, first create a Smithy Gradle project. 

> [!NOTE]
> You can use the `smithy init` [CLI](https://smithy.io/2.0/guides/smithy-cli/index.html) command to create a new
> Smithy Gradle project. The command `smithy init quickstart-gradle`  will create a new basic Smithy Gradle project.

Then apply the [`smithy-base`](https://smithy.io/2.0/guides/gradle-plugin/index.html#smithy-gradle-plugins) gradle plugin to 
your project.

```diff
// build.gradle.kts
plugins {
+    id("software.amazon.smithy.gradle.smithy-base") version "<replace with version>"
}
```

Then add the codegen plugins as a dependency of the `smithyBuild` configuration to make the plugins discoverable 
by the Smithy build process: 

```diff
dependencies {
+    smithyBuild("software.amazon.smithy.codegen:plugins:<replace with version>")
}
```

Now, configure your [`smithy-build`](https://smithy.io/2.0/guides/smithy-build-json.html) to use one of the 
Smithy Java codegen plugins. For example, to generate a client for a `CoffeeShop` service we would 
add the following to our `smithy-build.json`:

```diff
// smithy-build.json
{
    ...
    "plugins": {
+       "java-client-codegen": {
+            "service": "com.example#CoffeeShop"
+            "namespace": "software.amazon.smithy.java.examples",
+            "headerFile": "license.txt"
+       }
    }
}
```

Your project is now configured to generate Java code from our model. To execute a build run the 
gradle `build` task for your project. To compile the generated code as part of your project, 
add the generated package to your `main` sourceSet. For example:

```kotlin
// build.gradle.kts

afterEvaluate {
    val clientPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-client-codegen")
    sourceSets.main.get().java.srcDir(clientPath)
}

// Ensure client files are generated before java compilation is executed.
tasks.named("compileJava") {
    dependsOn("smithyBuild")
}
```

### Stability
Classes and API's annotated with `@SmithyInternal` are for internal use by Smithy-Java libraries and should **not** be
used by Smithy users. API's annotated with `@SmithyUnstableApi` are subject to change in future releases.

## Core modules and Interfaces
### Common
- `core` - Provides basic functionality for (de)serializing generated types and defining `Schema`'s, minimal representations
           of the Smithy data model for use at runtime.
- `io` - Common I/O functionality for clients/servers.
- `auth-api` - shared Authorization/Authentication API for clients and servers.
- `framework-errors` - Common errors that could be thrown by the Smithy Java framework.

### Client
- `client-core` - Provides protocol and transport agnostic functionality for clients. 
                  All generated clients require this package as a runtime dependency.
- `client-http` - Client-side implementation of HTTP transport.
- `dynamic-client` - Smithy client that exposes a dynamic API that doesn't require codegen.

### Server
- `server-core` - Provides protocol and transport agnostic functionality for servers. 
                  All generated server-stubs require this package as a runtime dependency.
- `server-netty` - Provides an HTTP server implementation using the [Netty](https://netty.io/) runtime.

### Codegen 
- `codegen:core` - Provides the basic framework necessary for codegen plugins and integrations to generate Java 
                   code from a Smithy model. Only codegen plugins and integrations should depend on this directly.
- `codegen:plugins` - Aggregate package that provides all code generation plugins.

Codegen integrations that modify the code generated by codegen plugins can be found in [codegen/integrations](codegen/integrations).

### Protocols
Smithy Java, like the Smithy IDL, is protocol-agnostic. Servers to support any number of protocols and clients can 
set the protocol to use at runtime.

The [`protocol-test-harness`](protocol-test-harness) package provides a framework for testing protocols.

#### Client 
- `client-rpcv2-cbor` - Implementation [rpcv2-cbor](https://smithy.io/2.0/additional-specs/protocols/smithy-rpc-v2.html#smithy-rpc-v2-cbor-protocol) protocol.
- `aws-client-awsjson` - Implementation of [AWS JSON 1.0](https://smithy.io/2.0/aws/protocols/aws-json-1_0-protocol.html#aws-json-1-0-protocol) and [AWS JSON 1.1](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html#aws-json-1-1-protocol) protocols.
- `aws-client-restjson` - Implementation of [AWS restJson1](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html#aws-json-1-1-protocol) protocol.
- `aws-client-restXml` - Implementation of  [AWS restXml](https://smithy.io/2.0/aws/protocols/aws-restxml-protocol.html#aws-restxml-protocol) protocol.

#### Server
- `server-rpcv2-cbor` - Implementation [rpcv2-cbor](https://smithy.io/2.0/additional-specs/protocols/smithy-rpc-v2.html#smithy-rpc-v2-cbor-protocol) protocol.
- `aws-server-restjson` - Implementation of [AWS restJson1](https://smithy.io/2.0/aws/protocols/aws-json-1_1-protocol.html#aws-json-1-1-protocol) protocol.

#### Codecs
Codecs provide basic (de)serialization functionality for protocols.
- `json-codec` - (de)serialization functionality for [JSON](https://www.json.org/json-en.html) format
- `cbor-codec` - Binary (de)serialization functionality for [CBOR](https://cbor.io/) format 
- `xml-codec` - (de)serialization functionality for [XML](https://www.w3.org/TR/REC-xml/) format

### Utilities 
- `jmespath` - [JMESPath](https://jmespath.org/) implementation that allows querying a `Document` using a JMESPath expression.


## Development
See [CONTRIBUTING](CONTRIBUTING.md) for more information on contributing to the Smithy-Java project.

### Pre-push hooks
Pre-push hooks are automatically added for unix users via the `addGitHooks` gradle task.

**Note**: In order to successfully execute the pre-defined hooks you must have the `smithy` CLI installed. 
See [installation instructions](https://smithy.io/2.0/guides/smithy-cli/cli_installation.html) if you do not already have the CLI installed.

## Security
If you discover a potential security issue in this project we ask that you notify AWS/Amazon Security via our 
[vulnerability reporting page](http://aws.amazon.com/security/vulnerability-reporting/). 
Please do **not** create a public GitHub issue.

## License
This project is licensed under the Apache-2.0 License.

