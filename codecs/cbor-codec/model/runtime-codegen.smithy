$version: "2"

namespace smithy.java.cbor.bench

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

    priority: Priority

    priorityList: PriorityList

    priorityMap: PriorityMap

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

/// Sparse, non-contiguous, and negative values so the generated reader cannot rely on a dense
/// tableswitch, and includes the boundaries an int codec is most likely to get wrong.
intEnum Priority {
    MIN = -2147483648
    NEGATIVE = -7
    ZERO = 0
    ONE = 1
    HUNDRED = 100
    MAX = 2147483647
}

list PriorityList {
    member: Priority
}

map PriorityMap {
    key: String
    value: Priority
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

/// Member names that codegen renders with folded acronyms, so ACL is read by getAcl() and
/// GrantReadACP by getGrantReadacP(). Resolving accessors off the raw model name finds nothing and
/// declines the shape, which covers most of S3. These names are taken from CopyObjectRequest.
structure AcronymStruct {
    ACL: String
    SSEKMSKeyId: String
    GrantReadACP: String
    ETag: String
    ChecksumCRC32: String
    BucketKeyEnabled: Boolean
    Tags: StringList
}

structure RecursiveStruct {
    value: String
    child: RecursiveStruct
}

/// Runtime codegen must refuse error shapes, because a generated reader bypasses the builder's
/// deserialize method, which is the only place the "came off the wire" flag is set.
@error("client")
structure BenchError {
    @required
    message: String

    code: Integer
}
