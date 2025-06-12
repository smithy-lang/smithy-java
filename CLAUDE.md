# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building and Testing
- `./gradlew build` - Build all modules and run tests
- `./gradlew test` - Run unit tests across all modules
- `./gradlew check` - Run all verification tasks (tests, linting, spotbugs)
- `./gradlew spotlessCheck` - Check code formatting
- `./gradlew spotlessApply` - Apply code formatting fixes
- `./gradlew spotbugsMain` - Run SpotBugs static analysis

### Module-Specific Commands
- `./gradlew :module-name:test` - Run tests for a specific module (e.g., `:core:test`)
- `./gradlew :module-name:build` - Build a specific module
- `./gradlew :examples:end-to-end:smithyBuild` - Generate code for examples

### Code Generation
- Code generation happens during the `smithyBuild` task
- Generated code is placed in `build/smithy/` directories
- Use `smithy-build.json` files to configure code generation

## Architecture Overview

### Core Framework Structure
This is a **Smithy Interface Definition Language (IDL)** implementation for Java with a layered architecture:

1. **Schema System** (`core/`) - Runtime representation of Smithy models with precompiled validation
2. **Code Generation** (`codegen/`) - Transforms Smithy models into Java client/server code  
3. **Protocol Layer** - Pluggable protocol implementations (REST JSON, RPC CBOR, AWS protocols)
4. **Transport Layer** - HTTP and other transport implementations
5. **Runtime Components** - Client/server frameworks that generated code depends on

### Key Architectural Patterns

**Plugin Architecture**: Uses Java ServiceLoader extensively for protocol, transport, and codec discovery via `META-INF/services/` files.

**Schema-Driven Runtime**: Smithy models compile to runtime `Schema` objects with precomputed validations for performance.

**Async-First Design**: All client operations return `CompletableFuture<T>`, servers use job-based orchestration.

**Layered Protocol Architecture**:
```
Generated Client/Server Code
       ↓
Protocol Layer (REST JSON, RPC CBOR, etc.)
       ↓  
Transport Layer (HTTP, etc.)
       ↓
Codec Layer (JSON, CBOR, XML)
```

### Module Organization

**Core Modules**:
- `core/` - Schema system, serialization, validation
- `codegen/` - Code generation framework and plugins
- `client/` - Client runtime framework with protocol/transport abstraction
- `server/` - Server framework with orchestrator pattern and handler architecture

**Protocol Implementations**:
- `client-rpcv2-cbor/` - Binary RPC protocol using CBOR serialization
- `aws/client/` - AWS protocol implementations (REST JSON, REST XML, AWS JSON)
- `codecs/` - Protocol-agnostic serialization (JSON, CBOR, XML)

**AWS Integration**:
- `aws/` - AWS-specific protocols, SigV4 auth, SDK v2 compatibility
- Service bundling tools for packaging AWS service models

**MCP Integration**:
- `mcp/` - Model Context Protocol server implementation
- Supports both direct handler and proxy modes

### Code Generation Flow

1. **Input**: Smithy model files (`.smithy`) and `smithy-build.json` configuration
2. **Processing**: CodeGenerationContext coordinates JavaSymbolProvider and specialized generators
3. **Output**: Generated Java classes in `build/smithy/` with runtime dependencies on core modules
4. **Integration**: JavaCodegenIntegration plugins extend generation process

### Testing Framework

- Uses JUnit 5 platform across all modules
- Protocol compliance testing via `protocol-test-harness`
- Integration tests in `src/it/` directories
- Examples serve as functional tests

## Development Guidelines

### Code Conventions
- Java 17+ required (toolchain uses Java 21, compiles to Java 17)
- Uses Spotless for formatting with Eclipse formatter
- SpotBugs for static analysis with custom filter rules
- License headers required on all files (auto-applied by Spotless)

### Module Structure
- Standard Gradle multi-module project
- Integration tests in `src/it/` directories  
- Test fixtures in `src/testFixtures/` where appropriate
- Resources in `src/main/resources/META-INF/` for service discovery

### Git Hooks
- Pre-push hooks automatically installed on Unix systems via `addGitHooks` task
- Requires `smithy` CLI to be installed for hook execution

### Important Implementation Notes

**Service Discovery**: New protocol/transport implementations must register via ServiceLoader in `META-INF/services/` files.

**Schema Performance**: The schema system precomputes validation constraints - avoid runtime constraint compilation.

**Generated Code Integration**: Generated clients/servers depend on corresponding runtime modules (`client-core`, `server-core`).

**Protocol Implementation**: Client and server protocol implementations are separate - both sides must be implemented for full protocol support.