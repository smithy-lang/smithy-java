### rpcv2-cbor-codec
Provides a codec for serializing or deserializing CBOR data to/from
Smithy-Java `SerializableShape`'s.

> [!NOTE]
> This codec follows the [Smithy RPCv2 CBOR specification](https://smithy.io/2.0/additional-specs/protocols/smithy-rpc-v2.html#shape-serialization)
> for the encoding of BigIntegers, BigDecimals, and Timestamps.

CBOR Protocol implementation can use this package to provide basic serde functionality.

On JDK 25 or newer, enable runtime-generated codecs with:

```java
Rpcv2CborCodec codec = Rpcv2CborCodec.builder()
        .runtimeCodegen(true)
        .build();
```

Unsupported models and generation failures transparently fall back to the built-in CBOR provider.
Explicit runtime-codegen activation cannot be combined with a custom provider.

The `smithy-java.runtime-codegen.cbor=enabled` system property enables runtime code generation
for codecs using the built-in provider.
