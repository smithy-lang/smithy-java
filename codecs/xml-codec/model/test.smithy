$version: "2"

namespace smithy.java.xml.test

/// A simple structure with scalar fields.
structure SimpleStruct {
    @required
    name: String

    @required
    age: Integer

    active: Boolean

    score: Double

    @timestampFormat("date-time")
    createdAt: Timestamp
}

/// A complex structure that exercises many XML features.
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

    @timestampFormat("date-time")
    createdAt: Timestamp

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

    color: Color

    colorList: ColorList

    bigIntValue: BigInteger

    bigDecValue: BigDecimal
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

enum Color {
    RED
    GREEN
    BLUE
    YELLOW
}

@sparse
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

/// Structure focused on numeric boundary testing
structure NumericStruct {
    byteVal: Byte
    shortVal: Short
    intVal: Integer
    longVal: Long
    floatVal: Float
    doubleVal: Double
    bigIntVal: BigInteger
    bigDecVal: BigDecimal
}

/// Structure focused on string edge cases
structure StringStruct {
    @required
    value: String
}

/// Structure with all three timestamp formats
structure TimestampStruct {
    epochSeconds: Timestamp

    @timestampFormat("date-time")
    dateTime: Timestamp

    @timestampFormat("http-date")
    httpDate: Timestamp
}

/// Structure with xmlName trait on fields
@xmlName("CustomRoot")
structure XmlNameStruct {
    @xmlName("ID")
    id: String

    @xmlName("DisplayName")
    displayName: String

    normalField: String
}

/// Structure with xmlAttribute trait
structure XmlAttributeStruct {
    @xmlAttribute
    version: String

    @xmlAttribute
    @xmlName("id")
    identifier: String

    content: String
}

/// Structure with xmlFlattened list
structure FlattenedListStruct {
    @xmlFlattened
    items: StringList

    @xmlFlattened
    numbers: IntegerList

    normalList: StringList
}

/// Structure with xmlFlattened map
structure FlattenedMapStruct {
    @xmlFlattened
    entries: StringMap

    normalMap: StringMap
}

/// Structure with xmlNamespace
@xmlNamespace(uri: "https://example.com/test")
structure NamespacedStruct {
    name: String
    value: Integer
}

/// Structure that nests itself for depth testing
structure RecursiveStruct {
    value: String
    child: RecursiveStruct
}

/// Structure for blob testing
structure BlobStruct {
    data: Blob
}

list DoubleList {
    member: Double
}

list BigDecimalList {
    member: BigDecimal
}

list BooleanList {
    member: Boolean
}

list ByteList {
    member: Byte
}

list ShortList {
    member: Short
}

list LongList {
    member: Long
}

list FloatList {
    member: Float
}

list BigIntegerList {
    member: BigInteger
}

list BlobList {
    member: Blob
}

list TimestampList {
    member: Timestamp
}

/// Structure containing lists of all types
structure AllListsStruct {
    booleans: BooleanList
    bytes: ByteList
    shorts: ShortList
    ints: IntegerList
    longs: LongList
    floats: FloatList
    doubles: DoubleList
    bigInts: BigIntegerList
    bigDecs: BigDecimalList
    strings: StringList
    blobs: BlobList
    timestamps: TimestampList
}
