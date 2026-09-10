## json-codec
Provides a codec for serializing or deserializing JSON data to/from
Smithy-Java `SerializableShape`'s.

JSON Protocol implementation can use this package to provide basic serde capabilities.

**Note:** This codec can discover custom `JsonSerdeProvider` service implementations via SPI. 
By default, [Jackson](https://github.com/FasterXML/jackson) is used to provide JSON serde.

On JDK 25 or newer, runtime-generated codecs can be enabled explicitly:

```java
JsonCodec codec = JsonCodec.builder()
        .runtimeCodegen(true)
        .build();
```

Unsupported models fall back to the native Smithy JSON provider. Explicit runtime-codegen
activation cannot be combined with a custom provider.

The `smithy-java.runtime-codegen` system property sets process-wide mode (`disabled`, `enabled`,
or `strict`), and `smithy-java.runtime-codegen.json` overrides it for JSON. `strict` rejects
fallbacks.
