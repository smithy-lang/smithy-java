# Change Log
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