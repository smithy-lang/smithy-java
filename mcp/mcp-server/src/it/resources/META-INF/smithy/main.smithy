$version: "2"

namespace smithy.java.mcp.test

use smithy.mcp#oneOf

service TestService {
    operations: [
        McpEcho,
    CalculateArea
    ]
}

operation McpEcho {
    input := {
        echo: Echo
    }
    output:= {
        echo: Echo
    }
}

operation CalculateArea {
    input : CalculateAreaInput

    output := {
        @required
        area: Double

        @required
        originalShape: Shape
    }
}

structure CalculateAreaInput {
    oneOfInput : Shape
}


/// Union without @oneOf trait - uses wrapper format natively
union Shape {
    circle : Circle
    square: Square
    rectangle: Rectangle
}

/// Document with @oneOf trait - for testing Document-based polymorphic types
/// Used when services need discriminator-based polymorphism
@oneOf(discriminator: "__type", members: [
    {name: "circle", target: Circle},
    {name: "square", target: Square},
    {name: "rectangle", target: Rectangle}
])
document ShapeWithOneOf

structure Circle {
    @required
    radius : Integer
}

structure Square {
    @required
    side: Integer
}

structure Rectangle {
    @required
    length: Integer
    @required
    breadth: Integer
}

/// A comprehensive structure containing all supported Smithy types for testing MCP serde
structure Echo {
    // Primitives
    stringValue: String
    booleanValue: Boolean
    byteValue: Byte
    shortValue: Short
    integerValue: Integer
    longValue: Long
    floatValue: Float
    doubleValue: Double

    // Big numbers (serialized as strings in JSON)
    bigDecimalValue: BigDecimal
    bigIntegerValue: BigInteger

    // Binary (serialized as base64 string)
    blobValue: Blob

    // Timestamps with different formats
    @timestampFormat("epoch-seconds")
    epochSecondsTimestamp: Timestamp
    @timestampFormat("date-time")
    dateTimeTimestamp: Timestamp
    @timestampFormat("http-date")
    httpDateTimestamp: Timestamp
    // Default timestamp (no format trait - defaults to date-time)
    defaultTimestamp: Timestamp

    // Collections
    stringList: StringList
    integerList: IntegerList
    nestedList: NestedEchoList

    // Maps
    stringMap: StringMap
    nestedMap: NestedEchoMap

    // Nested structure
    nested: NestedEcho

    // Document type (arbitrary JSON)
    documentValue: Document

    // Enum
    enumValue: TestEnum

    // IntEnum (integer enumeration)
    intEnumValue: TestIntEnum

    // Union type
    unionValue: TestUnion

    // Required field to test required validation
    @required
    requiredField: String
}

/// A nested structure for testing recursive types
structure NestedEcho {
    innerString: String
    innerNumber: Integer
    recursive: NestedEcho
}

list StringList {
    member: String
}

list IntegerList {
    member: Integer
}

list NestedEchoList {
    member: NestedEcho
}

map StringMap {
    key: String
    value: String
}

map NestedEchoMap {
    key: String
    value: NestedEcho
}

enum TestEnum {
    VALUE_ONE = "VALUE_ONE"
    VALUE_TWO = "VALUE_TWO"
}

intEnum TestIntEnum {
    ONE = 1
    TWO = 2
    THREE = 3
}

union TestUnion {
    stringOption: String
    integerOption: Integer
    nestedOption: NestedEcho
}
