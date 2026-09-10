$version: "2"

namespace smithy.java.aws.query.bench

use aws.protocols#ec2QueryName

/// Every scalar the Query serializer can write, in one shape, so the differential test covers all of
/// them in a single comparison.
structure ScalarStruct {
    stringValue: String

    booleanValue: Boolean

    byteValue: Byte

    shortValue: Short

    integerValue: Integer

    longValue: Long

    floatValue: Float

    doubleValue: Double

    bigIntegerValue: BigInteger

    bigDecimalValue: BigDecimal

    blobValue: Blob

    /// No @timestampFormat, so it takes the date-time default.
    timestampValue: Timestamp

    @timestampFormat("date-time")
    dateTimeValue: Timestamp

    @timestampFormat("epoch-seconds")
    epochValue: Timestamp

    @timestampFormat("http-date")
    httpDateValue: Timestamp

    enumValue: Suit

    intEnumValue: FaceCard
}

/// Primitives with defaults, which generated shapes write without a null guard.
structure PrimitiveStruct {
    @required
    primBoolean: PrimitiveBoolean = false

    @required
    primByte: PrimitiveByte = 0

    @required
    primShort: PrimitiveShort = 0

    @required
    primInteger: PrimitiveInteger = 0

    @required
    primLong: PrimitiveLong = 0

    @required
    primFloat: PrimitiveFloat = 0

    @required
    primDouble: PrimitiveDouble = 0

    boxedInteger: Integer
}

/// Renaming, which is the one place AWS Query and EC2 Query disagree about every member name.
structure RenameStruct {
    plain: String

    @xmlName("Renamed")
    renamed: String

    @ec2QueryName("Ec2Renamed")
    ec2Renamed: String

    @xmlName("BothRenamed")
    @ec2QueryName("Ec2BothRenamed")
    bothRenamed: String

    /// A name needing percent-encoding. Member names are encoded, list-member and map key/value
    /// names are not; the differential tests pin both behaviours as they stand.
    @xmlName("ns:OddName")
    oddName: String
}

/// Nesting deep enough that the generated writer folds a multi-segment static path.
structure NestedStruct {
    name: String
    inner: MiddleStruct
}

structure MiddleStruct {
    label: String
    inner: LeafStruct
}

structure LeafStruct {
    value: String
    numbers: IntegerList
}

/// Recursion, which has no finite set of static paths and so exercises the unfolding fallback.
structure RecursiveStruct {
    name: String
    next: RecursiveStruct
    children: RecursiveList
}

list RecursiveList {
    member: RecursiveStruct
}

structure ListStruct {
    strings: StringList

    @xmlFlattened
    flatStrings: StringList

    namedMembers: NamedMemberList

    @xmlFlattened
    flatNamedMembers: NamedMemberList

    structs: LeafList

    @xmlFlattened
    flatStructs: LeafList

    nested: ListOfLists

    @xmlFlattened
    flatNested: ListOfLists

    enums: SuitList

    timestamps: TimestampList

    blobs: BlobList

    sparse: SparseStringList

    stringSet: StringSet

    unions: UnionList
}

/// Maps, which EC2 Query cannot represent at all; the generated EC2 codec must refuse this shape and
/// leave it to the interpreted path.
structure MapStruct {
    strings: StringMap

    @xmlFlattened
    flatStrings: StringMap

    named: NamedEntryMap

    @xmlFlattened
    flatNamed: NamedEntryMap

    structs: LeafMap

    lists: ListMap

    enumKeyed: SuitKeyMap

    sparse: SparseStringMap

    mapOfMaps: MapMap
}

/// Three levels of collection, which is where the interpreted serializer's shared per-collection
/// state has to be saved and restored or an outer index skips.
structure NestedCollectionStruct {
    listOfMapOfList: ListMapList
}

/// A map nested under a list, which the EC2 refusal has to notice during traversal rather than only
/// at the top level.
structure MapUnderListStruct {
    maps: MapList
}

structure UnionStruct {
    choice: QueryUnion
    choices: UnionList
}

union QueryUnion {
    stringValue: String
    integerValue: Integer
    structValue: LeafStruct
    listValue: StringList
    enumValue: Suit
}

enum Suit {
    DIAMOND = "DIAMOND"
    CLUB = "CLUB"
    HEART = "HEART"
}

intEnum FaceCard {
    JACK = 1
    QUEEN = 2
    KING = 3
}

list StringList {
    member: String
}

@uniqueItems
list StringSet {
    member: String
}

list NamedMemberList {
    @xmlName("Item")
    member: String
}

list IntegerList {
    member: Integer
}

list SuitList {
    member: Suit
}

list TimestampList {
    @timestampFormat("epoch-seconds")
    member: Timestamp
}

list BlobList {
    member: Blob
}

list LeafList {
    member: LeafStruct
}

list ListOfLists {
    member: StringList
}

list UnionList {
    member: QueryUnion
}

list MapList {
    member: StringMap
}

list ListMapList {
    member: ListMap
}

@sparse
list SparseStringList {
    member: String
}

map StringMap {
    key: String
    value: String
}

map NamedEntryMap {
    @xmlName("EntryKey")
    key: String

    @xmlName("EntryValue")
    value: String
}

map LeafMap {
    key: String
    value: LeafStruct
}

map ListMap {
    key: String
    value: StringList
}

map SuitKeyMap {
    key: Suit
    value: String
}

map MapMap {
    key: String
    value: StringMap
}

@sparse
map SparseStringMap {
    key: String
    value: String
}
