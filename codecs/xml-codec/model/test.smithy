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

structure DenseListStruct {
    structs: StructItemList
    strings: PlainStringList
}

structure SparseListStruct {
    structs: SparseStructItemList
    strings: SparsePlainStringList
}

structure StructItem {
    id: String
}

list StructItemList {
    member: StructItem
}

@sparse
list SparseStructItemList {
    member: StructItem
}

list PlainStringList {
    member: String
}

@sparse
list SparsePlainStringList {
    member: String
}

intEnum Priority {
    LOW = 1
    MEDIUM = 5
    HIGH = 10
}

/// A union is framed as a structure holding exactly one member: no discriminator, no wrapper element.
union ValueUnion {
    stringValue: String

    intValue: Integer

    structValue: NestedStruct

    listValue: IntegerList

    mapValue: StringMap

    @xmlName("Renamed")
    renamedValue: String
}

list ValueUnionList {
    member: ValueUnion
}

/// Union members in every position a union can appear: alone, wrapped in a list, flattened, in a map.
structure UnionStruct {
    @required
    name: String

    choice: ValueUnion

    choices: ValueUnionList

    @xmlFlattened
    flatChoices: ValueUnionList
}

list PriorityList {
    member: Priority
}

map PriorityMap {
    key: String
    value: Priority
}

/// intEnum members: written as their integer value, not their name.
structure IntEnumStruct {
    priority: Priority
    priorities: PriorityList
    priorityMap: PriorityMap
}

/// Every type the inline attribute writer accepts, on an element that also carries a namespace.
///
/// The namespace has to be emitted before the attributes and the `>` deferred until after them, so
/// this shape is the one that pins down the open-tag byte order.
@xmlNamespace(uri: "https://example.com/attrs", prefix: "at")
structure TypedAttributeStruct {
    /// Required with a default, so it is always present and needs no null check.
    @required
    @xmlAttribute
    flag: PrimitiveBoolean = false

    @xmlAttribute
    boolAttr: Boolean

    @xmlAttribute
    intAttr: Integer

    @xmlAttribute
    longAttr: Long

    @xmlAttribute
    floatAttr: Float

    @xmlAttribute
    doubleAttr: Double

    @xmlAttribute
    @timestampFormat("date-time")
    timestampAttr: Timestamp

    @xmlAttribute
    colorAttr: Color

    @xmlAttribute
    priorityAttr: Priority

    @xmlAttribute
    @xmlName("renamed")
    renamedAttr: String

    body: String
}

list NamespacedList {
    @xmlNamespace(uri: "https://example.com/item", prefix: "i")
    @xmlName("Item")
    member: String
}

map NamespacedMap {
    @xmlNamespace(uri: "https://example.com/key", prefix: "k")
    @xmlName("Name")
    key: String

    @xmlNamespace(uri: "https://example.com/value", prefix: "v")
    @xmlName("Text")
    value: String
}

/// Namespaces on a member, on a list's item, and on a map's key and value.
@xmlNamespace(uri: "https://example.com/outer")
structure NamespacedMembersStruct {
    @xmlNamespace(uri: "https://example.com/member", prefix: "m")
    name: String

    items: NamespacedList

    @xmlFlattened
    flatItems: NamespacedList

    lookup: NamespacedMap

    @xmlFlattened
    flatLookup: NamespacedMap
}

/// An attribute whose resolved name carries a namespace prefix.
///
/// The prefix is part of the name that is written, but the parser records attributes under their local
/// name, so reading has to look for `someName` while writing emits `xsi:someName`. This shape only
/// appears nested, because a prefixed attribute on a root element is not read by the dispatch
/// deserializer at all: it looks that one up through the struct's name table, which holds the full
/// name.
structure PrefixedAttributeStruct {
    @xmlAttribute
    @xmlName("xsi:someName")
    prefixed: String

    value: String
}

/// Attribute members that memberIndex order places behind an element member.
///
/// Attributes can only be written while the start tag is still open, so their position relative to the
/// elements is the one place member order is semantically meaningful in XML. `body` is required with no
/// default, which hoists it to the front of `schema.members()` even though both attributes are declared
/// ahead of it, so this shape only serializes correctly if the writer partitions members itself instead
/// of following the order the plan hands it.
structure AttributeAfterElementStruct {
    @xmlAttribute
    lateAttr: String

    @xmlAttribute
    @xmlName("renamed")
    otherAttr: Integer

    @required
    body: String

    @xmlNamespace(uri: "https://example.com", prefix: "xsi")
    nested: PrefixedAttributeStruct

    trailing: String
}
