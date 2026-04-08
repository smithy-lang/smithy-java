$version: "2"

namespace smithy.java.json.bench

/// A simple structure with only scalar fields for baseline benchmarks.
structure SimpleStruct {
    @required
    name: String

    @required
    age: Integer

    active: Boolean

    score: Double

    createdAt: Timestamp
}

/// A complex structure that exercises many Smithy type features.
structure ComplexStruct {
    @required
    id: String

    @required
    count: Integer

    @required
    enabled: PrimitiveBoolean = false

    @required
    ratio: PrimitiveDouble = 0

    @required
    score: PrimitiveFloat = 0

    @required
    bigCount: PrimitiveLong = 0

    optionalString: String

    optionalInt: Integer

    createdAt: Timestamp

    @timestampFormat("date-time")
    updatedAt: Timestamp

    @timestampFormat("http-date")
    expiresAt: Timestamp

    payload: Blob

    tags: StringList

    intList: IntegerList

    metadata: StringMap

    intMap: IntegerMap

    @required
    nested: NestedStruct

    optionalNested: NestedStruct

    structList: NestedStructList

    structMap: NestedStructMap

    choice: BenchUnion

    color: Color

    colorList: ColorList

    sparseStrings: SparseStringList

    sparseMap: SparseStringMap

    bigIntValue: BigInteger

    bigDecValue: BigDecimal

    freeformData: Document
}

structure NestedStruct {
    @required
    field1: String

    @required
    field2: Integer

    inner: InnerStruct
}

structure InnerStruct {
    value: String
    numbers: IntegerList
}

union BenchUnion {
    stringValue: String
    intValue: Integer
    structValue: NestedStruct
}

enum Color {
    RED
    GREEN
    BLUE
    YELLOW
}

list StringList {
    member: String
}

list IntegerList {
    member: Integer
}

list NestedStructList {
    member: NestedStruct
}

list ColorList {
    member: Color
}

@sparse
list SparseStringList {
    member: String
}

map StringMap {
    key: String
    value: String
}

map IntegerMap {
    key: String
    value: Integer
}

map NestedStructMap {
    key: String
    value: NestedStruct
}

@sparse
map SparseStringMap {
    key: String
    value: String
}
