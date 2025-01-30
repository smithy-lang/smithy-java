## client-core
This module provides the core functionality required to execute the client 
request pipeline. This module is used by both code generated and dynamic clients. 

**Note:** Code generated clients require this module as a runtime dependency.

The functionality provided by this module includes: 
- Auth Scheme API and Auth Scheme Resolution
- Identity Resolution
- Endpoint API and Endpoint Resolution 
- Client Interceptors
- Pagination
- Exception Mapping
- Client Plugin API 
- Client Transport API 
- Client Protocol API
- Default Client Plugins
- Base Client settings

To generate a client see the [client-codegen plugin](../codegen/plugins/client).
To use a dynamic client see the [dynamic-client](../dynamic-client) module.
