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

/// Circle structure with optional nested shapes for testing recursive @oneOf adaptation
structure CircleWithNested {
    @required
    radius : Integer

    /// List of nested shapes (for testing recursive @oneOf document adaptation)
    nestedShapes: ShapeWithOneOfList

    /// Timestamp union for testing timestamp serialization in @oneOf documents
    timestampUnion: TimestampUnion
}

/// @oneOf document with nested list of @oneOf documents
@oneOf(discriminator: "__type", members: [
    {name: "circleWithNested", target: CircleWithNested},
    {name: "square", target: Square},
    {name: "rectangle", target: Rectangle}
])
document NestedShapeWithOneOf

/// List of nested @oneOf documents
list NestedShapeWithOneOfList {
    member: NestedShapeWithOneOf
}

/// Union containing different timestamp formats for testing timestamp serialization
union TimestampUnion {
    @timestampFormat("epoch-seconds")
    epochSecondsTimestamp: Timestamp
    @timestampFormat("date-time")
    dateTimeTimestamp: Timestamp
    @timestampFormat("http-date")
    httpDateTimestamp: Timestamp
    defaultTimestamp: Timestamp
}

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

    // Timestamp union with different formats
    timestampUnion: TimestampUnion

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

    // String with enum trait (constrained string values)
    stringEnumValue: StringWithEnumTrait

    // IntEnum (integer enumeration)
    intEnumValue: TestIntEnum

    // Union type
    unionValue: TestUnion

    // Union in collections (for testing nested union adaptation)
    unionList: UnionList
    unionMap: UnionMap

    // @oneOf document in collections (for testing Document-based polymorphic types)
    shapeWithOneOfList: ShapeWithOneOfList
    shapeWithOneOfMap: ShapeWithOneOfMap

    // Nested @oneOf documents (for testing recursive adaptation)
    nestedShapeWithOneOf: NestedShapeWithOneOf
    nestedShapeWithOneOfList: NestedShapeWithOneOfList

    // Helper to make CircleWithNested reachable for schema generation
    circleWithNested: CircleWithNested

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

@sparse
list StringList {
    member: String
}

@sparse
list IntegerList {
    member: Integer
}

@sparse
list NestedEchoList {
    member: NestedEcho
}

@sparse
map StringMap {
    key: String
    value: String
}

@sparse
map NestedEchoMap {
    key: String
    value: NestedEcho
}

enum TestEnum {
    VALUE_ONE = "VALUE_ONE"
    VALUE_TWO = "VALUE_TWO"
}

/// A string with enum trait for testing enum values on string shapes
@enum([
    { value: "OPTION_A", name: "OPTION_A" },
    { value: "OPTION_B", name: "OPTION_B" },
    { value: "OPTION_C", name: "OPTION_C" }
])
string StringWithEnumTrait

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

/// List of unions for testing nested union adaptation
list UnionList {
    member: TestUnion
}

/// Map of unions for testing nested union adaptation
map UnionMap {
    key: String
    value: TestUnion
}

/// List of @oneOf documents for testing Document-based polymorphic types in collections
list ShapeWithOneOfList {
    member: ShapeWithOneOf
}

/// Map of @oneOf documents for testing Document-based polymorphic types in collections
map ShapeWithOneOfMap {
    key: String
    value: ShapeWithOneOf
}
