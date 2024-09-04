$version: "2"

namespace trials

structure Trials {
    allFieldsOptional: AllFieldsOptional
    attributeUpdates: AttributeUpdates
    codegenStruct: CodegenStruct
    sendMessageRequest: SendMessageRequest
}

// ------- AllFieldsOptional ------- //
structure AllFieldsOptional {
    string1: String
    string2: String
    string3: String
    string4: String
    string5: String
    string6: String
}

// ------- Attribute Updates ------- //
structure AttributeUpdates {
    @required
    requestId: String

    @required
    attributes: AttributeMetadataSet

    @required
    timestamp: String

    // TODO: move this and related tests to an actual timestamp
    metadata: AttributeValueUpdateMetadata
}

@sparse
map AttributeMetadataSet {
    key: String
    value: AttributeMetadataValue
}

structure AttributeMetadataValue {
    @required
    value: UpdateAttributeValue

    @required
    edgeSubscriptionId: String

    @required
    timestamp: String

    // TODO: move this and related tests to an actual timestamp
    metadata: AttributeValueUpdateMetadata
}

map AttributeValueUpdateMetadata {
    key: String
    value: String
}

union UpdateAttributeValue {
    stringValue: String
    longValue: Long
    doubleValue: Double
    booleanValue: Boolean
    longList: LongList
    stringList: StringList
    doubleList: DoubleList
    booleanList: BooleanList
    complexAttributeValue: String
}

list LongList {
    member: Long
}

list StringList {
    member: String
}

list DoubleList {
    member: Double
}

list BooleanList {
    member: Boolean
}

// ------------------- Struct test ---------------------------------------
structure CodegenStruct {
    @required
    string: String

    @required
    stringMap: StringMap

    structList: StructList

    @required
    requiredStruct: CodegenOptionalStruct

    // unsigned int
    @required
    @range(min: 0)
    i: PrimitiveInteger = 0

    // unsigned long
    @required
    @range(min: 0)
    l: PrimitiveLong = 0

    // signed int to show difference in formats that can
    // serialize signed and unsigned integer types differently
    @required
    signedI: PrimitiveInteger = 0

    @required
    d: PrimitiveDouble = 0

    @required
    f: PrimitiveFloat = 0

    optionalInt: Integer

    optionalLong: Long

    optionalStructList: OptionalStructList

    optionalStruct: CodegenStruct

    @required
    bool1: PrimitiveBoolean = false

    intList: IntList

    blob: Blob

    stringList: StringList

    floatList: FloatList

    doubleList: DoubleList

    intMap: IntMap

    optionalStructMap: OptionalStructMap

    time: Timestamp

    unionMap: UnionMap

    enumList: EnumList

    intEnumField: IntegerEnum

    intEnumList: IntegerEnumList

    intEnumMap: IntegerEnumMap

    enumMap: StringEnumMap

    sparseStringList: SparseStringList

    sparseStructMap: SparseStructMap

    sparseStructList: SparseStructList
}

@sparse
map SparseStructMap {
    key: String
    value: CodegenOptionalStruct
}

intEnum IntegerEnum {
    ZERO = 0
    ONE = 1
    TWO = 2
}

list IntegerEnumList {
    member: IntegerEnum
}

map IntegerEnumMap {
    key: String
    value: IntegerEnum
}

list EnumList {
    member: StringEnum
}

enum StringEnum {
    ONE
    TWO
    THREE
    FOUR
    FIVE
}

map StringEnumMap {
    key: String
    value: StringEnum
}

map UnionMap {
    key: String
    value: UnionStruct
}

union UnionStruct {
    string: String
    integer: Integer
}

structure CodegenOptionalStruct {
    @required
    string: String

    @required
    timestamp: Double
}

map StringMap {
    key: String
    value: String
}

list StructList {
    member: CodegenStruct
}

list OptionalStructList {
    member: CodegenOptionalStruct
}

@sparse
list SparseStructList {
    member: CodegenOptionalStruct
}

list IntList {
    member: Integer
}

list LongList {
    member: Long
}

list ByteList {
    member: Byte
}

list ShortList {
    member: Short
}

list FloatList {
    member: Float
}

list StringList {
    member: String
}

@sparse
list SparseStringList {
    member: String
}

map IntMap {
    key: String
    value: Integer
}

map OptionalStructMap {
    key: String
    value: CodegenOptionalStruct
}

// ------------- SendMessageRequest ----------------
structure SendMessageRequest {
    queueId: String
    clusterHostId: String
    messageId: String
    messageBody: Blob
    messageMd5: Blob
    messageAttributes: Blob
    messageAttributesMd5: Blob
    customerId: String
    delaySeconds: Integer
    messageSystemAttributes: Blob
    messageSystemAttributesMd5: Blob
    messageMeteringSize: Long
}
