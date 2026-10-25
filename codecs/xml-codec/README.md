## xml-codec
Provides a codec for serializing or deserializing XML data to/from 
Smithy-Java `SerializableShape`'s.

XML Protocol implementation can use this package to provide basic serde.

By default, Smithy Java's native implementation is used to provide XML serde. The StAX
implementation can be selected using `-Dsmithy-java.xml-provider=stax`.
