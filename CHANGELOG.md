# Change Log

## 1.4.0 (6/10/2026)
> [!IMPORTANT]
> All client modules are considered stable.  Some modules, including
> server, CLI, and MCP, are still in developer-preview and may contain
> bugs.  No guarantee is made about their API stability. Unstable
> modules are marked with a warning in their `README.md` and with the
> `@SmithyUnstableApi` annotation in their `package-info.java`.

### Features

- Added support for getter sections in union codegen, allowing codegen integrations to customize the generated union value getters. ([#1229](https://github.com/smithy-lang/smithy-java/pull/1229))
- Added call interceptors that can intercept and control the full lifecycle of a client call, enabling middleware-style behaviors such as caching and hedging. Interceptors can also override the operation input. ([#1218](https://github.com/smithy-lang/smithy-java/pull/1218))
- Added a `discard()` method to `DataStream` to drain unconsumed streams when needed. ([#1224](https://github.com/smithy-lang/smithy-java/pull/1224))
- Added `x-amz-content-sha256` handling for S3 and S3 Express requests. ([#1217](https://github.com/smithy-lang/smithy-java/pull/1217))

### Bug Fixes

- Fixed several `DynamicClient` serialization and deserialization issues, including comparing document-typed values by content, serializing renamed input/output shapes under their original wire name, and preserving null entries in `@sparse` maps and lists. ([#1233](https://github.com/smithy-lang/smithy-java/pull/1233))
- Fixed `PathSerializer` to support non-string types in HTTP label bindings. ([#1232](https://github.com/smithy-lang/smithy-java/pull/1232))
- Added a maximum depth limit to CBOR deserialization to guard against stack exhaustion from deeply nested input. ([#1226](https://github.com/smithy-lang/smithy-java/pull/1226))

### Improvements

- Optimized the `writeTo` implementation for multi-buffer data streams. ([#1224](https://github.com/smithy-lang/smithy-java/pull/1224))
- Reduced allocations during SigV4 request signing by switching to in-place buffer encoding on the signing hot path. ([#1217](https://github.com/smithy-lang/smithy-java/pull/1217))

## 1.3.0 (5/26/2026)
> [!IMPORTANT]
> All client modules are considered stable.  Some modules, including
> server, CLI, and MCP, are still in developer-preview and may contain
> bugs.  No guarantee is made about their API stability. Unstable
> modules are marked with a warning in their `README.md` and with the
> `@SmithyUnstableApi` annotation in their `package-info.java`.

### Features

- Improved Java HTTP client integration. ([#1206](https://github.com/smithy-lang/smithy-java/pull/1206))

### Bug Fixes

- Fixed NPE in JsonCodec.deserializeShape that suppresses deserialization errors. ([#1203](https://github.com/smithy-lang/smithy-java/pull/1203))
- Moved version SPI into dedicated module to avoid classpath conflicts. ([#1213](https://github.com/smithy-lang/smithy-java/pull/1213))

## 1.2.0 (5/20/2026)

### Features
- Added custom constraints validation support for shapes. ([#1171](https://github.com/smithy-lang/smithy-java/pull/1171))
- Added `McpServerInterceptor` with scoped hooks for MCP servers. ([#1174](https://github.com/smithy-lang/smithy-java/pull/1174))
- Added `AuthScheme` and `IdentityResolver` support to `HttpMcpProxy`. ([#1169](https://github.com/smithy-lang/smithy-java/pull/1169))
- Inferred MCP `ToolAnnotations` from `@readonly` and `@idempotent` traits. ([#1157](https://github.com/smithy-lang/smithy-java/pull/1157))
- Added support for Smithy RPC v2 JSON protocol. ([#1092](https://github.com/smithy-lang/smithy-java/pull/1092))
- Added support for EC2 Query protocol. ([#1160](https://github.com/smithy-lang/smithy-java/pull/1160))
- Implemented standard and adaptive retry strategies. ([#1149](https://github.com/smithy-lang/smithy-java/pull/1149))
- Added a mechanism to avoid mixing versions for a client. ([#1167](https://github.com/smithy-lang/smithy-java/pull/1167))
- Added service schema to `ProtocolSettings` for service-level traits. ([#1166](https://github.com/smithy-lang/smithy-java/pull/1166))
- Added AWS and EC2 Query schema extensions. ([#1187](https://github.com/smithy-lang/smithy-java/pull/1187))

### Bug Fixes
- Fixed `DateTimeException` in CBOR timestamp deserialization. ([#1183](https://github.com/smithy-lang/smithy-java/pull/1183))
- Used deferred writes to avoid interpreting dollar sign as format. ([#1191](https://github.com/smithy-lang/smithy-java/pull/1191))
- Fixed query protocols to ignore the body if the output schema has the unit type trait. ([#1173](https://github.com/smithy-lang/smithy-java/pull/1173))
- Fixed javadoc formatting. ([#1168](https://github.com/smithy-lang/smithy-java/pull/1168))
- Added setting `integrationSettings` to `CodegenDirector` when codegen is being executed. ([#1151](https://github.com/smithy-lang/smithy-java/pull/1151))
- Added explicit dependency on `framework-errors` in codegen-plugin. ([#1153](https://github.com/smithy-lang/smithy-java/pull/1153))

### Improvements
- Optimized HTTP bindings ([#1194](https://github.com/smithy-lang/smithy-java/pull/1194), [#1192](https://github.com/smithy-lang/smithy-java/pull/1192), [#1190](https://github.com/smithy-lang/smithy-java/pull/1190))
- Optimized CBOR codec. ([#1183](https://github.com/smithy-lang/smithy-java/pull/1183))
- Optimized query and URI encoding/validation. ([#1190](https://github.com/smithy-lang/smithy-java/pull/1190))
- Cached struct payload check for HTTP bindings. ([#1189](https://github.com/smithy-lang/smithy-java/pull/1189))
- Optimized JSON codec. ([#1148](https://github.com/smithy-lang/smithy-java/pull/1148))
- Improved JDK HTTP client transport performance. ([#1163](https://github.com/smithy-lang/smithy-java/pull/1163))
- Optimized HTTP binding and XML overhead. ([#1182](https://github.com/smithy-lang/smithy-java/pull/1182))
- Pooled HTTP codec path and cached empty-body output. ([#1182](https://github.com/smithy-lang/smithy-java/pull/1182))
- Improved codegen performance. ([#1177](https://github.com/smithy-lang/smithy-java/pull/1177))

### Other
- Marked `CborSchemaExtensions` as internal. ([#1183](https://github.com/smithy-lang/smithy-java/pull/1183))

## 1.1.0 (4/8/2026)
> [!IMPORTANT]
> All client modules are considered stable.  Some modules, including
> server, CLI, and MCP, are still in developer-preview and may contain
> bugs.  No guarantee is made about their API stability. Unstable
> modules are marked with a warning in their `README.md` and with the
> `@SmithyUnstableApi` annotation in their `package-info.java`.

### Features
- Made input/output structs implement `Closeable` when they have streaming members. ([#1138](https://github.com/smithy-lang/smithy-java/pull/1138))
- Added new opcodes to endpoints VM ([#1141](https://github.com/smithy-lang/smithy-java/pull/1141))

### Bug Fixes
- Fixed content-type serialization for lists. ([#1144](https://github.com/smithy-lang/smithy-java/pull/1144))
- Fixed placeholder character escaping in trait values. ([#1139](https://github.com/smithy-lang/smithy-java/pull/1139))
- Fixed hashcode variable name clash. ([#1133](https://github.com/smithy-lang/smithy-java/pull/1133))

### Improvements
- Optimized ARN handling with a lazy map. ([#1132](https://github.com/smithy-lang/smithy-java/pull/1132))
- Optimized endpoint resolution with benchmarks. ([#1135](https://github.com/smithy-lang/smithy-java/pull/1135))
- Optimized equals for faster short-circuiting. ([#1136](https://github.com/smithy-lang/smithy-java/pull/1136))
- Optimized the handling of URLs for the rules engine ([#1143](https://github.com/smithy-lang/smithy-java/pull/1143))

### Other
- Consumed AWS API models from Maven instead of checking out from git. ([#1140](https://github.com/smithy-lang/smithy-java/pull/1140))
- Published a BOM (Bill of Materials) for dependency management. ([#1137](https://github.com/smithy-lang/smithy-java/pull/1137))

## 1.0.0 (04/2/2026)
> [!IMPORTANT]
> This is the first GA release of Smithy Java. All client modules are now considered stable.
> Some modules, including server, CLI, and MCP, are still in developer-preview and may contain bugs.
> No guarantee is made about their API stability. Unstable modules are marked with a warning in their
> `README.md` and with the `@SmithyUnstableApi` annotation in their `package-info.java`.

### Features
- Added JSpecify nullness annotation support.
- Added endpoint rules engine with BDD codegen, fused opcodes, and peephole optimizations. ([#1035](https://github.com/smithy-lang/smithy-java/pull/1035))
- Added support for AWS Query protocol in the client.
- Added support for request compression. ([#968](https://github.com/smithy-lang/smithy-java/pull/968))
- Added checksum support in requests. ([#911](https://github.com/smithy-lang/smithy-java/pull/911))
- Added event streams support to AWS JSON protocols. ([#1076](https://github.com/smithy-lang/smithy-java/pull/1076))
- Added event stream implementation for RPCv2. ([#849](https://github.com/smithy-lang/smithy-java/pull/849))
- Added support for `@eventHeader` and `@eventPayload` for RPC protocols. ([#864](https://github.com/smithy-lang/smithy-java/pull/864))
- Added event stream signing support. ([#1054](https://github.com/smithy-lang/smithy-java/pull/1054))
- Implemented new event streams API. ([#1035](https://github.com/smithy-lang/smithy-java/pull/1035))
- Added an OpenTelemetry based plugin to publish operation metrics. ([#1030](https://github.com/smithy-lang/smithy-java/pull/1030))
- Added support for native remote MCP servers. ([#943](https://github.com/smithy-lang/smithy-java/pull/943))
- Added MCP ping request support. ([#1016](https://github.com/smithy-lang/smithy-java/pull/1016))
- Added MCP proxy server support for prompts.
- Added MCP metrics observer support to McpServerBuilder.
- Added support for SSE + streaming with MCP notifications.
- Added structured content to MCP tool responses when possible.
- Added output schema to MCP tools/list.
- Added protocol version to MCP initialize response.
- Added workingDirectory to StdioProxy.
- Added error response parsing for restXml. ([#922](https://github.com/smithy-lang/smithy-java/pull/922))
- Added support for BigDecimal Documents in CBOR.
- Added map input for dynamic client.
- Added `DataStream.writeTo()` and IO utilities.
- Added `isAvailable` to check if DataStream has not been consumed.
- Added close to DataStream to allow closing resources.
- Added generic types to JMESPath predicates. ([#1067](https://github.com/smithy-lang/smithy-java/pull/1067))
- Added utility to fill shapes with random values.
- Added fuzz testing framework.
- Added document discriminator sanitizer for JSON protocols. ([#932](https://github.com/smithy-lang/smithy-java/pull/932))
- Added plugin phases, auto-plugins, and hierarchy for codegen.
- Added ability to register default plugins in codegen.
- Added new code sections to support deserialization overrides.
- Added plugin test runner. ([#855](https://github.com/smithy-lang/smithy-java/pull/855))
- Added compile method for EndpointRuleSet in RulesEngineBuilder.
- Added singletons for AwsCredentialsResolver implementations. ([#1059](https://github.com/smithy-lang/smithy-java/pull/1059))
- Added strategy for writing error types to headers.
- Added ModifiableHttpHeaders method to overwrite HTTP header values.
- Generated input shape with additionalInput member for operations with Unit input.
- Added model bundle version to decide whether to wrap input or not.
- Added additionalInfo to BundleMetadata.
- Allowed event processors to compose. ([#1095](https://github.com/smithy-lang/smithy-java/pull/1095))
- Allowed lists to be marked as httpPayload.
- Added KEYS synchronization.
- Added HttpTransportConfig for transport settings.

### Bug Fixes
- Fixed equals for float and double values. ([#1129](https://github.com/smithy-lang/smithy-java/pull/1129))
- Fixed CBOR deserializer crash on empty input for no-input operations. ([#1103](https://github.com/smithy-lang/smithy-java/pull/1103))
- Fixed protocol initialization for AwsRestJson1Protocol. ([#1023](https://github.com/smithy-lang/smithy-java/pull/1023))
- Fixed incorrect codegen for HttpApiKeyAuthTrait.
- Fixed encoding/decoding of errors and exception events. ([#1055](https://github.com/smithy-lang/smithy-java/pull/1055))
- Fixed SchemaIndex collision for Unit inputs/outputs.
- Fixed handling of timestamps inside oneOf unions.
- Fixed handling of event payload members of blob and string type. ([#1064](https://github.com/smithy-lang/smithy-java/pull/1064))
- Fixed bug of adding a null element to the list when list is empty or null.
- Fixed Sigv4 multivalued query key ordering and double path encoding. ([#984](https://github.com/smithy-lang/smithy-java/pull/984))
- Fixed query params with more than one value per key. ([#973](https://github.com/smithy-lang/smithy-java/pull/973))
- Fixed double encoding URIs for signing. ([#972](https://github.com/smithy-lang/smithy-java/pull/972))
- Fixed httpQuery related protocol tests. ([#945](https://github.com/smithy-lang/smithy-java/pull/945))
- Fixed httpHeader and httpPrefixHeaders protocol tests. ([#914](https://github.com/smithy-lang/smithy-java/pull/914))
- Fixed QueryCustomizedError protocol test.
- Fixed type conversion issues in MCP.
- Fixed naming conflict between class name and Schema field when structure name is all capitals.
- Fixed name conflicts resolving. ([#902](https://github.com/smithy-lang/smithy-java/pull/902))
- Fixed builder setters to use the correct boxed or primitive type.
- Fixed javadoc link. ([#987](https://github.com/smithy-lang/smithy-java/pull/987))
- Fixed URI concat issue with HttpClientProtocol.
- Fixed bytecode loading and creation.
- Fixed O(N!) allocations for recursive union variants.
- Fixed content-type for http-payload when already in headers.
- Fixed skipping OperationGenerator in types-only codegen mode.
- Fixed gradle to generate javadoc and sources jar for publishing. ([#854](https://github.com/smithy-lang/smithy-java/pull/854))
- Fixed delay publishing the initial event until fully wired. ([#865](https://github.com/smithy-lang/smithy-java/pull/865))
- Fixed null protocol version handling in MCP requests.
- Fixed proxy initialization failures no longer silently swallowed.
- Correctly handled BigInteger and BigDecimal defaults.
- Correctly adapted oneOf unions nested in oneOfUnions.
- Properly supported lists of Documents. ([#869](https://github.com/smithy-lang/smithy-java/pull/869))

### Improvements
- Optimized hashCode computation. ([#1130](https://github.com/smithy-lang/smithy-java/pull/1130))
- Cleaned up query/percent parsing and encoding utilities.
- Removed indirection and overhead from interceptors ([#1127](https://github.com/smithy-lang/smithy-java/pull/1127))
- Optimized header name matching using generated buckets and identity checks.
- Optimized equals and hashCode of SmithyUri.
- Optimized request/response pipeline handling.
- Replaced request/response builders with modifiable copies.
- Switched to faster and cheaper array-backed headers.
- Optimized Context implementation with chunked array storage.
- Optimized SchemaConverter and MCP Schema conversion.
- Optimized string validation.
- Optimized endpoints VM with fused opcodes, `ite`, negative indexing, and `SPLIT` opcode.
- Optimized timestamp handling with minor improvements.
- Avoided unnecessary UTF-8 decoding and byte[] allocations when flushing MCP structs.
- Avoided boxing for primitive Documents and fixed equals for various Documents.
- Used SmithyUri instead of URI for performance.
- Improved HttpHeaders to only allocate names when needed.
- Added upper bound on container pre-allocation during deserialization.
- Prevented allocating large arrays based on BigInteger lengths.
- Added stricter verifications while reading lists in Json Codec.
- Added better header and value validation.
- Improved client content-type deserialization handling.
- Converted Jackson exceptions to SerializationException.
- Used JmespathRuntime for JMESPath evaluation.
- Used proper classloader for loading services. ([#958](https://github.com/smithy-lang/smithy-java/pull/958))
- Used symbols instead of hardcoded strings. ([#1072](https://github.com/smithy-lang/smithy-java/pull/1072))
- Used sealed interfaces for enums and records for unions.
- Oriented DataStream around InputStream.
- Made Client implement Closeable.
- Made ClientTransport extend Closeable.
- Made rules engine independent of client.
- Moved to blocking client, towards virtual threads.
- Increased default MCP HTTP proxy timeout from 60s to 5 minutes.
- Upgraded to Jackson 3.0.3.

### Breaking Changes
- Removed request/response builders in favor of modifiable copies.
- Removed `useExternalTypes` option (unused).
- Removed unused utility methods.
- Removed tracing API. ([#1068](https://github.com/smithy-lang/smithy-java/pull/1068))
- Removed ExternalSymbols.
- Removed MCP CLI functionality.
- Removed MCP bundles module.
- Removed unused ExecutorService from call context.
- Removed map* methods from Hooks and replaced with cast.
- Removed default from JsonArraySchema.
- Removed unused jline dependency.
- Split codegen-plugins back into codegen-core and removed client-api. ([#1091](https://github.com/smithy-lang/smithy-java/pull/1091))
- Moved from plugin-based codegen to mode-based codegen for types, client, and server.
- Moved endpoints to upper level.
- Separated java and resources into different folders. ([#1078](https://github.com/smithy-lang/smithy-java/pull/1078))
- Surface area review of client packages. ([#1086](https://github.com/smithy-lang/smithy-java/pull/1086))
- Surface area review of CLI package API. ([#1085](https://github.com/smithy-lang/smithy-java/pull/1085))
- Made errorSchemas non-default in the interface.
- Made union members implicitly type-cast in getValue.
- Made additionalProperties a Document.

## 0.0.3 (08/21/2025)
> [!WARNING]
> This is a developer-preview release and may contain bugs. **No** guarantee is made about API stability.
> This release is not recommended for production use!
### Features
- Added Model Context Protocol (MCP) CLI to manage MCP Servers.
- Added Model Context Protocol (MCP) server generation from Smithy models with AWS service bundles and generic bundles.
- Added @prompts trait to provide contextual guidance for LLMs when using Smithy services.
- Added @tool_assistant and @install_tool prompts for enhanced AI integration.
- Added CORS headers support for Netty server request handler.
- Added Document support and BigDecimal/BigInteger support for RPCv2 CBOR protocol.
- Added pretty printing support for JSON serialization.
- Added client-config command to configure client configurations.
- Added request-level override capabilities and call config interceptor support.
- Added JMESPath to_number function.
- Added dynamic tool loading and search tools support.
- Added StdIo MCP proxy and process I/O proxy functionality.
- Added support for customizing smithy mcp home directory.

### Bug Fixes
- Fixed empty prefix headers tests.
- Changed generated getters to have 'get' prefix.
- Fixed smithy-call issues.
- Fixed Registry selection and injection.
- Fixed missing newline in generated code.
- Fixed cross-branch pollution bug in SchemaConverter recursion detection.
- Fixed incorrect bitmask exposed by DeferredMemberSchema.
- Fixed service file merging for mcp-schemas.
- Fixed message in generated exceptions when message field has different casing.
- Fixed bounds check when non-exact bytebuffer is passed.
- Fixed ResourceGenerator to handle resources with more than 10 properties.
- Fixed JSON Documents equals implementation.
- Fixed CSV header parsing to not include quotes.
- Fixed AWS model bundle operation filtering logic.
- Fixed integration tests and flaky test issues.
- Fixed README to have correct example names for lambda example.

### Improvements
- Made JAR builds reproducible.
- Updated to build with JDK21 while targeting JDK17.
- Added Graal metadata generation for native CLI builds.
- Added version provider SPI and exit code telemetry.

## 0.0.2 (04/07/2025)
> [!WARNING]
> This is a developer-preview release and may contain bugs. **No** guarantee is made about API stability.
> This release is not recommended for production use!
### Features
- Added generation of root-level service-specific exceptions for clients and servers.
- Added usage examples.
- Added custom exceptions for ClientTransport.
- Added lambda endpoint functionality with updated naming.
- Consolidated Schemas into a single class.
- Updated namespace and module naming.
- Improved document serialization of wrapped structures.
- Added support for service-schema access from client-side.
- Added support for generating MCP and MCP-proxy servers


## 0.0.1 (02/06/2025)
> [!WARNING]
> This is a developer-preview release and may contain bugs. **No** guarantee is made about API stability.
> This release is not recommended for production use!
### Features
- Implemented Client, Server and Type codegen plugins.
- Added Client event streaming support.
- Added Client Auth support - sigv4, bearer auth, http basic auth.
- Added JSON protocol support - restJson1, awsJson.
- Added RPCV2 CBOR protocol support.
- Implemented Dynamic client that can load a Smithy model to call a service.
- Added Smithy Lambda Endpoint wrapper to run generated server stubs in AWS Lambda.
