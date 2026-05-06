# HTTP Layer

> **Last updated:** April 29, 2026

The HTTP layer provides the abstraction between Smithy's protocol-agnostic model and the wire format. It's split into
four modules: core HTTP types, HTTP binding (mapping Smithy traits to HTTP), the Java HttpClient transport, and
client-side HTTP binding protocol support.

**Source:**
- [`http/http-api/`](https://github.com/smithy-lang/smithy-java/tree/main/http/http-api) — Core HTTP types
- [`http/http-binding/`](https://github.com/smithy-lang/smithy-java/tree/main/http/http-binding) — Smithy HTTP trait
  mapping
- [`client/client-http/`](https://github.com/smithy-lang/smithy-java/tree/main/client/client-http) — Java HttpClient
  transport
- [`client/client-http-binding/`](https://github.com/smithy-lang/smithy-java/tree/main/client/client-http-binding) —
  Client HTTP binding protocol base

## HTTP Abstraction (`http-api`)

### HttpRequest and HttpResponse

Both extend `HttpMessage` and follow an **immutable-by-default, modifiable-on-demand** pattern:

```java
public interface HttpMessage extends AutoCloseable {
    HttpVersion httpVersion();
    HttpHeaders headers();
    DataStream body();              // never null, may be zero-length
}

public interface HttpRequest extends HttpMessage {
    String method();
    SmithyUri uri();
    ModifiableHttpRequest toModifiable();     // returns self if already modifiable
    ModifiableHttpRequest toModifiableCopy(); // always copies
    HttpRequest toUnmodifiable();             // freezes
    static ModifiableHttpRequest create();
}

public interface HttpResponse extends HttpMessage {
    int statusCode();
    ModifiableHttpResponse toModifiable();
    ModifiableHttpResponse toModifiableCopy();
    HttpResponse toUnmodifiable();
    static ModifiableHttpResponse create();
}
```

Modifiable variants add fluent setters: `setMethod()`, `setUri()`, `setStatusCode()`, `setBody()`, `setHeader()`, `addHeader()`, `removeHeader()`.

### HttpHeaders

Case-insensitive header container:

```java
public interface HttpHeaders {
    static HttpHeaders of(Map<String, List<String>> headers);       // immutable
    static ModifiableHttpHeaders ofModifiable();                     // mutable
    boolean hasHeader(String name);
    String firstValue(String name);
    List<String> allValues(String name);
    void forEachEntry(BiConsumer<String, String> consumer);         // most efficient iteration
    ModifiableHttpHeaders toModifiable();
    HttpHeaders toUnmodifiable();
}
```

Backed by flat arrays (`ArrayHttpHeaders` / `ArrayUnmodifiableHttpHeaders`) for O(1) iteration.

### HeaderName

Canonical lowercase header name constants with fast case-insensitive lookup. Pre-defines ~60+ standard HTTP
headers. `canonicalize(String)` returns the canonical constant for known headers (O(1) by length-bucketed lookup),
lowercased string for unknown. Uses ASCII `| 0x20` trick for branchless case-insensitive comparison.

### MessageExchange

```java
public final class HttpMessageExchange implements MessageExchange<HttpRequest, HttpResponse> {
    public static final HttpMessageExchange INSTANCE = new HttpMessageExchange();
}
```

A singleton marker that types the request/response pair. Both transport and protocol expose `messageExchange()`, and the
pipeline validates they match.

## HTTP Binding (`http-binding`)

This module maps Smithy `@http*` traits to HTTP request/response components. It is protocol-agnostic — body
serialization is delegated to a `Codec`.

### Entry Point

```java
public final class HttpBinding {
    public RequestSerializer requestSerializer();
    public ResponseSerializer responseSerializer();
    public RequestDeserializer requestDeserializer();
    public ResponseDeserializer responseDeserializer();
}
```

### BindingMatcher

Pre-computes the binding location for each member of a structure schema using `member.memberIndex()` for O(1) lookup:

```java
enum Binding { HEADER, QUERY, PAYLOAD, BODY, LABEL, STATUS, PREFIX_HEADERS, QUERY_PARAMS }
```

Trait-to-binding mapping:
- `@httpLabel` → LABEL (URI path segment)
- `@httpQuery` → QUERY (query parameter)
- `@httpQueryParams` → QUERY_PARAMS (map of query params)
- `@httpHeader` → HEADER
- `@httpPrefixHeaders` → PREFIX_HEADERS (map of headers with prefix)
- `@httpPayload` → PAYLOAD (entire body)
- `@httpResponseCode` → STATUS
- No trait → BODY (serialized in body alongside other BODY members)

`BindingMatcher` instances are cached per `Schema` in `ConcurrentHashMap`s.

### Request Serialization Flow

```java
httpBinding.requestSerializer()
    .operation(operation)
    .payloadCodec(codec)
    .payloadMediaType("application/json")
    .shapeValue(input)
    .endpoint(endpoint)
    .omitEmptyPayload(true)
    .serializeRequest();  // → HttpRequest
```

Internally:
1. Gets `BindingMatcher` from cache
2. Creates `HttpBindingSerializer` with the `HttpTrait`, codec, matcher
3. Calls `shapeValue.serialize(serializer)` → triggers `writeStruct()`
4. Each member is routed to the appropriate sub-serializer:
   - HEADER → `HttpHeaderSerializer`
   - QUERY → `HttpQuerySerializer` (writes to `QueryStringBuilder`)
   - LABEL → `HttpLabelSerializer` (writes to labels map)
   - PAYLOAD → `PayloadSerializer` (handles DataStream, EventStream, or codec)
   - BODY → serialized via codec (filtered to only BODY-bound members)
5. Builds URI from endpoint + path labels + query string
6. Constructs `HttpRequest` with method, URI, headers, body

### Response Deserialization Flow

```java
httpBinding.responseDeserializer()
    .payloadCodec(codec)
    .outputShapeBuilder(builder)
    .response(response)
    .deserialize();
```

Dispatches each member by binding location:
- HEADER → `HttpHeaderDeserializer`
- STATUS → `ResponseStatusDeserializer`
- PAYLOAD → handles event streams, streaming DataStream, or codec-deserialized shapes
- BODY → codec-deserialized (filtered to only BODY-bound members)

## Java HttpClient Transport (`client-http`)

### JavaHttpClientTransport

The primary transport implementation, wrapping `java.net.http.HttpClient`:

```java
public final class JavaHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {
    public JavaHttpClientTransport();
    public JavaHttpClientTransport(HttpClient client);
    public HttpResponse send(Context context, HttpRequest request);
}
```

Request construction:
- Creates `BodyPublisher`: `noBody()` for empty, `ofByteArray()` for known-length, `ofInputStream()` for streaming
- Applies per-request timeout from `HttpContext.HTTP_REQUEST_TIMEOUT`
- Copies headers via `forEachEntry()`, skipping `content-length` (JDK manages it)

Response parsing:
- Converts `java.net.http.HttpResponse<InputStream>` to Smithy `HttpResponse`
- Wraps response body as `DataStream.ofInputStream()`

Error mapping: `HttpConnectTimeoutException` → `ConnectTimeoutException`, other exceptions via `ClientTransport.remapExceptions()`.

Discovered via SPI (`ClientTransportFactory`, name `"http-java"`).

### HttpClientProtocol

Abstract base for HTTP-based protocols:

```java
public abstract class HttpClientProtocol implements ClientProtocol<HttpRequest, HttpResponse> {
    public MessageExchange<HttpRequest, HttpResponse> messageExchange(); // → HttpMessageExchange.INSTANCE
    public HttpRequest setServiceEndpoint(HttpRequest request, Endpoint endpoint);
}
```

`setServiceEndpoint()` merges the endpoint URI with the request URI and copies endpoint-provided HTTP headers.

### HttpBindingClientProtocol

Ties together `HttpClientProtocol` + `HttpBinding` for REST-style protocols:

```java
public abstract class HttpBindingClientProtocol<F extends Frame<?>> extends HttpClientProtocol {
    abstract protected String payloadMediaType();
    abstract protected Codec payloadCodec();
    abstract protected HttpErrorDeserializer getErrorDeserializer(Context context);
}
```

Default `createRequest()` delegates to `HttpBinding.requestSerializer()`. Default `deserializeResponse()` checks status
(200-299 = success), uses `HttpBinding.responseDeserializer()` for success, `HttpErrorDeserializer` for errors.

### HttpErrorDeserializer

Configurable error deserialization pipeline:

```java
HttpErrorDeserializer.builder()
    .codec(codec)
    .headerErrorExtractor(extractor)    // check headers for error type
    .knownErrorFactory(factory)          // deserialize known errors
    .unknownErrorFactory(factory)        // create unknown errors
    .errorPayloadParser(parser)          // extract error ShapeId from payload
    .build();
```

Resolution order: headers → payload parsing → fallback to HTTP status code.

### Plugins

- `UserAgentPlugin` — Sets User-Agent header
- `HttpChecksumPlugin` — Computes/validates HTTP checksums
- `RequestCompressionPlugin` — Gzip compression for request bodies
- `ApplyHttpRetryInfoPlugin` — Adds retry info headers

### Built-in Auth Schemes

- `HttpBearerAuthScheme` / `HttpBearerAuthSigner`
- `HttpBasicAuthAuthScheme` / `HttpBasicAuthSigner`
- `HttpApiKeyAuthScheme` / `HttpApiKeyAuthSigner`
- `HttpDigestAuthAuthScheme` / `HttpDigestAuthSigner`

## Key Design Patterns

1. **Immutable/Modifiable duality** — All HTTP messages have immutable and modifiable variants. `toModifiable()` returns
   self if already modifiable; `toModifiableCopy()` always copies.

2. **Binding caching** — `BindingMatcher` instances are cached per `Schema` to avoid re-computing trait lookups on every
   request.

3. **Codec delegation** — The HTTP binding layer is protocol-agnostic. Body serialization is delegated to a `Codec`
   (JSON, CBOR, XML).

4. **InterceptingSerializer pattern** — `HttpBindingSerializer` routes each struct member to the correct sub-serializer
   based on its binding location.

5. **MessageExchange as type witness** — Ensures type-safe pairing of transport and protocol.
