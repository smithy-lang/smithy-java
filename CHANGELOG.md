# Change Log

## 0.0.2 (03/21/2025)
> [!WARNING]
> This is a developer-preview release and may contain bugs. **No** guarantee is made about API stability. 
> This release is not recommended for production use!

### Features
- Added generation of root-level service-specific exceptions for clients and servers.
- Added usage examples.
- Added custom exceptions for ClientTransport.
- Added lambda endpoint functionality with updated naming.
- Consolidated Schemas into a single class.


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

