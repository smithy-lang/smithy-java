# AWS Protocol Integrations

> **Last updated:** April 29, 2026

Smithy-Java implements four AWS-specific protocols as `ClientProtocol` plugins, plus a shared event streaming
infrastructure. These protocols differ in serialization format, operation routing, and HTTP binding usage, but all share
the same client pipeline and auth system.

**Source:** [`aws/client/`](https://github.com/smithy-lang/smithy-java/tree/main/aws/client)

## Protocol Hierarchy

```
ClientProtocol<HttpRequest, HttpResponse>
  └── HttpClientProtocol (abstract)
        ├── HttpBindingClientProtocol<F> (abstract, REST-style)
        │     ├── RestJsonClientProtocol     — aws.protocols#restJson1
        │     └── RestXmlClientProtocol      — aws.protocols#restXml
        ├── AwsJsonProtocol (abstract sealed, RPC-style)
        │     ├── AwsJson1Protocol           — aws.protocols#awsJson1_0
        │     └── AwsJson11Protocol          — aws.protocols#awsJson1_1
        └── AwsQueryClientProtocol           — aws.protocols#awsQuery
```

All protocols are registered via `ClientProtocolFactory` SPI in `META-INF/services`.

## AWS JSON 1.0 / 1.1

**Module:** `aws-client-awsjson`

RPC-style protocol, all data goes in the body, no HTTP binding traits.

- **Request**: Always `POST`. Sets `X-Amz-Target: {ServiceName}.{OperationName}`. Body is JSON-serialized
  input. Content-Type: `application/x-amz-json-1.0` or `1.1`.
- **Response**: JSON body. Empty body → deserialize from `{}`.
- **Error detection**: `x-amzn-errortype` header first, then `__type` and `code` fields in JSON body. JSON 1.0 strips
  URI prefix from `__type`.
- **Codec**: `JsonCodec` with `useTimestampFormat(true)` but NOT `useJsonName(true)`.

## AWS restJson1

**Module:** `aws-client-restjson`

REST-style protocol, uses HTTP binding traits (`@http`, `@httpHeader`, `@httpQuery`, `@httpLabel`, `@httpPayload`,
`@httpPrefixHeaders`).

- **Request**: HTTP method and URI pattern from `@http` trait. Headers, query params, path labels from binding
  traits. Body is JSON for non-bound members.
- **Response**: HTTP binding deserialization for headers, status code, payload.
- **Error detection**: `x-amzn-errortype` header first, then `__type` in JSON body. Uses `HttpBindingErrorFactory` for
  known errors.
- **Codec**: `JsonCodec` with `useJsonName(true)` AND `useTimestampFormat(true)`.
- **Key difference from AWS JSON**: Uses `@jsonName` trait, omits empty payloads, supports struct payloads via
  `@httpPayload`.

## AWS restXml

**Module:** `aws-client-restxml`

REST-style protocol, same HTTP binding support as restJson1 but with XML body.

- **Request/Response**: Same HTTP binding pattern as restJson1 but body is XML.
- **Error detection**: `x-amzn-errortype` header first, then XML error code via `XmlUtil.parseErrorCodeName()`.
- **Codec**: `XmlCodec`.

## AWS Query

**Module:** `aws-client-awsquery`

The most unique protocol, asymmetric serialization formats.

- **Request**: Always `POST` with `Content-Type: application/x-www-form-urlencoded`. Body format:
  `Action={OperationName}&Version={version}&Param1=Value1&Param2.SubParam=Value2`. Uses custom `AwsQueryFormSerializer`
  with dot-delimited nested parameters. Respects `@xmlName` and `@xmlFlattened` traits.
- **Response**: XML body with wrapper elements (`{OperationName}Response` → `{OperationName}Result`). Uses `XmlCodec`
  with wrapper element configuration.
- **Error detection**: XML error code via `XmlUtil.parseErrorCodeName()`. Checks `@awsQueryError` trait custom codes on
  operation error schemas.
- **Requires**: Both `service` and `serviceVersion` settings (unlike other protocols).
- **Does NOT support**: Event streaming, document types.

## Event Streaming

**Module:** `aws-event-streams`

All AWS protocols (except Query) share the same event streaming infrastructure based on the AWS Event Stream binary
message format (`application/vnd.amazon.eventstream`).

### Core Types

- `AwsEventFrame` — wraps `software.amazon.eventstream.Message`, implements `Frame<Message>`
- `AwsEventEncoderFactory` — creates encoders for input/output streams
- `AwsEventDecoderFactory` — creates decoders for input/output streams

### Encoding Flow

1. Determine if event is initial request/response or a union event member
2. Handle `@eventHeader` members → event message headers
3. Handle `@eventPayload` members → blob (raw bytes), string (UTF-8), or codec-serialized
4. Regular members → codec-serialized as payload
5. Error events: modeled exceptions get `:exception-type` header; unmodeled get `:error-code` + `:error-message`

### Decoding Flow

1. Read `:message-type` header: `"event"`, `"error"`, or `"exception"`
2. For errors: extract `:error-code` and `:error-message`, throw `EventStreamingException`
3. For exceptions: read `:exception-type`, deserialize as modeled error
4. For events: read `:event-type`, find matching union member, deserialize payload + headers

### RPC vs REST Event Streaming

- **RPC protocols** (AWS JSON, rpcv2-cbor): Use `RpcEventStreamsUtil` helper for body wrapping and initial event
  handling
- **REST protocols** (restJson1, restXml): Use `HttpBindingClientProtocol`'s built-in event streaming via
  `RequestSerializer.eventEncoderFactory()` and `ResponseDeserializer.eventDecoderFactory()`

## Protocol Comparison

| Aspect | AWS JSON 1.0/1.1 | restJson1 | restXml | AWS Query |
|--------|-------------------|-----------|---------|-----------|
| Style | RPC | REST | REST | RPC |
| Request body | JSON | JSON + HTTP bindings | XML + HTTP bindings | Form URL-encoded |
| Response body | JSON | JSON + HTTP bindings | XML + HTTP bindings | XML with wrappers |
| Operation routing | `X-Amz-Target` header | HTTP method + URI | HTTP method + URI | `Action=` in body |
| `@jsonName` | No | Yes | N/A | N/A |
| `@xmlName` | N/A | N/A | Yes | Yes |
| Empty input | `{}` | Omitted | Omitted | `Action=Op&Version=V` |
| Event streaming | Yes | Yes | Yes | No |
| Required settings | `service` | `service` | `service` | `service` + `serviceVersion` |

## Key Design Patterns

1. **RPC vs REST split**: RPC protocols extend `HttpClientProtocol` directly. REST protocols extend
   `HttpBindingClientProtocol` which delegates to `HttpBinding` for HTTP trait-based serialization.

2. **Shared event streaming**: All protocols use the same
   `AwsEventFrame`/`AwsEventEncoderFactory`/`AwsEventDecoderFactory` infrastructure.

3. **SPI-based discovery**: All protocol factories are registered via `ClientProtocolFactory` SPI, enabling runtime
   protocol selection.

4. **Error header priority**: All AWS protocols check `x-amzn-errortype` header before parsing the response body for
   error type.
