# I/O and Streaming

> **Last updated:** April 29, 2026

The I/O module provides the foundational abstractions for binary data transfer in Smithy-Java: `DataStream` for byte
streams and `EventStream` for typed event sequences. These abstractions unify how streaming shapes flow through the
client/server pipeline — the transport layer only sees `DataStream`, regardless of whether the payload is a simple blob
or a sequence of modeled events.

**Source:** [`io/`](https://github.com/smithy-lang/smithy-java/tree/main/io),
  [`core/src/main/java/software/amazon/smithy/java/core/serde/event/`](https://github.com/smithy-lang/smithy-java/tree/main/core/src/main/java/software/amazon/smithy/java/core/serde/event)

## DataStream

[`DataStream`](https://github.com/smithy-lang/smithy-java/blob/main/io/src/main/java/software/amazon/smithy/java/io/datastream/DataStream.java)
is the central abstraction for reading streams of binary data. It extends `Flow.Publisher<ByteBuffer>` and
`AutoCloseable`.

```java
public interface DataStream extends Flow.Publisher<ByteBuffer>, AutoCloseable {
    long contentLength();           // -1 if unknown
    boolean hasKnownLength();
    String contentType();           // MIME type or null
    boolean isReplayable();         // can re-read from beginning
    boolean isAvailable();          // hasn't been consumed yet (or is replayable)
    InputStream asInputStream();    // blocking InputStream view
    void writeTo(OutputStream out); // transfer to OutputStream
    ByteBuffer asByteBuffer();      // read all into memory
}
```

### Factory Methods

```java
DataStream.ofEmpty()                                    // singleton empty stream
DataStream.ofString("hello", "text/plain")              // from string
DataStream.ofBytes(bytes, "application/octet-stream")   // from byte array
DataStream.ofByteBuffer(buffer, "application/cbor")     // from ByteBuffer
DataStream.ofInputStream(is, contentType, contentLength) // from InputStream
DataStream.ofFile(path, contentType)                    // from file
DataStream.ofPublisher(publisher, contentType, length, replayable) // from reactive publisher
```

### Implementations

| Class | Replayable | Known Length | Notes |
|-------|-----------|-------------|-------|
| `EmptyDataStream` | ✅ | ✅ (0) | Singleton |
| `ByteBufferDataStream` | ✅ | ✅ | Uses `buffer.duplicate()` for replay |
| `FileDataStream` | ✅ | ✅ | Opens new `InputStream` per read |
| `InputStreamDataStream` | ❌ | Depends | Tracks `consumed` flag, throws on re-read |
| `PublisherDataStream` | Configurable | Configurable | Wraps `Flow.Publisher<ByteBuffer>` |
| `WrappedDataStream` | Delegates | Overridden | Decorator for metadata changes |

DataStream is used as the body type for all HTTP messages. Event streams are converted to `DataStream` via `EventPipeStream`, unifying the transport layer.

### Replayability and Retries

`isReplayable()` is critical for retry decisions. In `ClientCall.isRetryDisallowed()`, if the input has a non-replayable
data stream (blob streaming), retries are prevented. Event streams (union type) are excluded from this check.

## EventStream

[`EventStream<T>`](https://github.com/smithy-lang/smithy-java/blob/main/core/src/main/java/software/amazon/smithy/java/core/serde/event/EventStream.java)
is a sealed interface for typed event sequences:

```
EventStream<T extends SerializableStruct> (sealed)
├── EventStreamReader<T> — reading events (implements Iterable<T>)
│   └── ProtocolEventStreamReader<IE, T> — protocol-level reader
│       └── DefaultEventStreamReader<IE, T, F>
└── EventStreamWriter<T> — writing events
    └── ProtocolEventStreamWriter<T, IE, F> — protocol-level writer
        └── DefaultEventStreamWriter<IE, T, F>
```

### EventStreamWriter — Writing Events

```java
public sealed interface EventStreamWriter<T extends SerializableStruct> extends EventStream<T> {
    void write(T event); // blocks until write is possible
}
```

The protocol-level writer adds internal methods:
- `toDataStream()` — converts writer to a DataStream for wire transport
- `setEventEncoderFactory(EventEncoderFactory)` — configures encoding
- `setInitialEvent(IE)` — sets the first event
- `addFrameProcessor(FrameProcessor)` — adds signing/transformation
- `activate()` — finalizes setup, unblocks writes

### EventStreamReader — Reading Events

```java
public sealed interface EventStreamReader<T extends SerializableStruct> extends Iterable<T>, EventStream<T> {
    T read();  // returns null at end of stream
}
```

### Two-Phase Initialization

Writers use a two-phase initialization pattern:

1. **Configuration phase**: `setEventEncoderFactory()` → `setInitialEvent()` → `addFrameProcessor()`
2. **Activation**: `activate()` unblocks the `readyLatch`, allowing user writes

This is necessary because the protocol and auth layers must configure encoding and signing before any events can be
written. A `CountDownLatch` blocks user writes until `activate()` is called.

## Frame Encoding/Decoding Architecture

Events are encoded into frames for wire transport:

```java
interface Frame<T> { T unwrap(); }
interface FrameEncoder<F extends Frame<?>> { ByteBuffer encode(F frame); }
interface FrameDecoder<F extends Frame<?>> { List<F> decode(ByteBuffer buffer); } // stateful
interface FrameProcessor<F extends Frame<?>> {
    F transformFrame(F frame);      // e.g., signing
    default F closingFrame();       // e.g., SigV4 trailing frame
}
```

Event encoding/decoding:

```java
interface EventEncoder<F extends Frame<?>> {
    F encode(SerializableStruct item);
    F encodeFailure(Throwable exception);
}
interface EventDecoder<F extends Frame<?>> {
    SerializableStruct decode(F frame);
    SerializableStruct decodeInitialEvent(F frame, EventStream<?> stream);
}
```

`ProcessingEventEncoderFactory` is a decorator that wraps factories with `FrameProcessor` instances (e.g., for SigV4
event signing). Multiple processors compose via chained `withFrameProcessor()` calls.

### EventPipeStream

`EventPipeStream` is the thread-safe bridge between the event-writing thread and the transport-reading thread:

- `ArrayBlockingQueue<ByteBuffer>` with capacity 16
- `write(ByteBuffer)` blocks if queue full
- `complete()` sends a `POISON_PILL` sentinel (empty ByteBuffer)
- `read()` blocks on `queue.take()`, returns -1 at POISON_PILL

## How Streaming Flows Through the Pipeline

### Client-Side Request (Event Streaming)

1. `Client.call()` extracts the `EventStream` from the input and casts to `ProtocolEventStreamWriter`
2. Protocol serializes input, configures the writer with `EventEncoderFactory`
3. `writer.toDataStream()` creates a `DataStream` backed by `EventPipeStream`
4. After signing, `writer.addFrameProcessor(eventSigner)` adds SigV4 event signing
5. `writer.activate()` unblocks writes, sends initial event
6. Transport reads from the `DataStream` while user writes events on another thread

### Client-Side Response (Event Streaming)

1. Protocol creates `ProtocolEventStreamReader.newReader(body, eventDecoderFactory, false)`
2. User iterates over `EventStreamReader<T>` using `read()` or `iterator()`
3. Reader reads from `DataStream.asInputStream()`, decodes frames, decodes events

### Data Stream (Simpler)

- **Serialization**: `writeDataStream()` sets the DataStream directly as the HTTP body
- **Deserialization**: `readDataStream()` returns the response body DataStream directly
- No encoding/decoding pipeline — raw bytes flow through

## Schema Integration

Streaming shapes are identified by the `@streaming` trait:

```java
// In ApiOperation:
default Schema inputStreamMember() {
    for (var m : inputSchema().members()) {
        if (m.hasTrait(TraitKey.STREAMING_TRAIT)) return m;
    }
    return null;
}
```

The distinction between data streams and event streams is made by checking `Schema.type()`:
- `ShapeType.UNION` → event stream
- Otherwise → data stream (blob)

## Codec Integration

```java
// ShapeSerializer
default void writeDataStream(Schema schema, DataStream value) { /* no-op */ }
default void writeEventStream(Schema schema, EventStream<?> value) { /* no-op */ }

// ShapeDeserializer
default DataStream readDataStream(Schema schema) { throw UnsupportedOperationException; }
default EventStream<?> readEventStream(Schema schema) { throw UnsupportedOperationException; }
```

HTTP binding serialization routes streaming members specially — `DataStream` is set as the HTTP body directly, while `EventStream` is converted via the writer pipeline.

## I/O Utilities

### ByteBufferUtils

```java
static String base64Encode(ByteBuffer buffer);
static String getUTF8String(ByteBuffer buffer);
static byte[] getBytes(ByteBuffer buffer);       // zero-copy if array-backed and exact
static InputStream byteBufferInputStream(ByteBuffer buffer);
```

### ByteBufferOutputStream

Growable `OutputStream` backed by a `byte[]` array:

```java
ByteBuffer toByteBuffer();      // wraps internal array (no copy)
void writeTo(OutputStream out); // direct write from internal array
void reset();                   // reuse buffer without reallocation
```

### SmithyUri

Lightweight, pre-decomposed URI representation optimized for endpoint-style URIs. Stores scheme, host, port, path, query
separately. Lazy caching of `toString()`, `hashCode()`, `toURI()`. Key methods: `withScheme()`, `withHost()`,
`withPath()`, `withConcatPath()`, `withEndpoint()`.

### URLEncoding

RFC 3986 percent-encoding: `encodeUnreserved(String, boolean preserveSlashes)` and `urlDecode(String)`.

### QueryStringBuilder / QueryStringParser

Build and parse query strings with proper encoding. `QueryStringBuilder.addForQueryParams()` skips keys already added
via `add()` (httpQuery takes precedence).
