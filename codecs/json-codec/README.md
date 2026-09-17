## json-codec
Provides a codec for serializing or deserializing JSON data to/from
Smithy-Java `SerializableShape`'s.

JSON Protocol implementation can use this package to provide basic serde capabilities.

**Note:** This codec can discover custom `JsonSerdeProvider` service implementations via SPI. 
By default, Smithy Java's native implementation is used to provide JSON serde. Jackson can be
selected using `-Dsmithy-java.json-provider=jackson`.
