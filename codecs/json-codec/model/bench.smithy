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

    wireLength: WireLengthEnum

    wireLengthList: WireLengthEnumList

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

/// Exercises every wire-length branch of the generated enum reader: values shorter than one
/// 8-byte word, exactly one word, spanning two words, and longer than two words. Also covers
/// values needing JSON escapes and non-ASCII bytes, which take the decode-first path.
enum WireLengthEnum {
    ONE = "a"
    SEVEN = "abcdefg"
    EIGHT = "abcdefgh"
    NINE = "abcdefghi"
    SIXTEEN = "abcdefghijklmnop"
    SEVENTEEN = "abcdefghijklmnopq"
    LONG = "abcdefghijklmnopqrstuvwxyz0123456789"
    SHARED_PREFIX_EIGHT = "abcdefgx"
    SHARED_PREFIX_NINE = "abcdefghx"
    SHARED_PREFIX_LONG = "abcdefghijklmnopqx"
    QUOTE = "a\"b"
    BACKSLASH = "a\\b"
    NON_ASCII_SHORT = "ü"
    NON_ASCII = "café-ünïcøde"
}

list WireLengthEnumList {
    member: WireLengthEnum
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

/// Structure with jsonName trait on multiple fields
structure JsonNameStruct {
    @jsonName("ID")
    id: String

    @jsonName("DisplayName")
    displayName: String

    normalField: String
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

map IntToStructMap {
    key: String
    value: InnerStruct
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

/// Structure containing lists of all types to exercise ListElementSerializer
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
