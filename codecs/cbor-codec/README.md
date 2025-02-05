### rpcv2-cbor-codec
Provides a codec for serializing or deserializing CBOR data to/from
Smithy-Java `SerializableShape`'s.

> [!NOTE]
> This codec follows the [Smithy RPCv2 CBOR specification](https://smithy.io/2.0/additional-specs/protocols/smithy-rpc-v2.html#shape-serialization)
> for the encoding of BigIntegers, BigDecimals, and Timestamps.

CBOR Protocol implementation can use this package to provide basic serde functionality.
