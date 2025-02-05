## json-codec
Provides a codec for serializing or deserializing JSON data to/from
Smithy-Java `SerializableShape`'s.

JSON Protocol implementation can use this package to provide basic serde capabilities.

**Note:** This codec can discover custom `JsonSerdeProvider` service implementations via SPI. 
By default, [Jackson](https://github.com/FasterXML/jackson) is used to provide JSON serde.
