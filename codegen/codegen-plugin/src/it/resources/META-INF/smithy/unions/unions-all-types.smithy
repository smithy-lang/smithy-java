$version: "2"

namespace smithy.java.codegen.test.unions

use smithy.java.codegen.test.common#ListOfString
use smithy.java.codegen.test.common#NestedEnum
use smithy.java.codegen.test.common#NestedIntEnum
use smithy.java.codegen.test.common#NestedStruct
use smithy.java.codegen.test.common#NestedUnion
use smithy.java.codegen.test.common#StringStringMap

operation UnionAllTypes {
    input := {
        union: UnionType
    }
}

union UnionType {
    blobValue: Blob
    booleanValue: Boolean
    listValue: ListOfString
    mapValue: StringStringMap
    bigDecimalValue: BigDecimal
    bigIntegerValue: BigInteger
    byteValue: Byte
    doubleValue: Double
    floatValue: Float
    integerValue: Integer
    longValue: Long
    shortValue: Short
    stringValue: String
    structureValue: NestedStruct
    timestampValue: Timestamp
    unionValue: NestedUnion
    enumValue: NestedEnum
    intEnumValue: NestedIntEnum
    unitValue: Unit
}
