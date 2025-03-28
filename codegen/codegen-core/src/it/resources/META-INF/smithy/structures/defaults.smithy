$version: "2"

namespace smithy.java.codegen.test.structures

use smithy.java.codegen.test.common#ListOfString
use smithy.java.codegen.test.common#NestedEnum
use smithy.java.codegen.test.common#NestedIntEnum
use smithy.java.codegen.test.common#StreamingBlob
use smithy.java.codegen.test.common#StringStringMap

operation Defaults {
    input := {
        @default(true)
        boolean: Boolean

        @default(1)
        bigDecimal: BigDecimal

        @default(1)
        bigInteger: BigInteger

        @default(1)
        byte: Byte

        @default(1.0)
        double: Double

        @default(1.0)
        float: Float

        @default(1)
        integer: Integer

        @default(1)
        long: Long

        @default(1)
        short: Short

        @default("default")
        string: String

        @default("YmxvYg==")
        blob: Blob

        @default("c3RyZWFtaW5n")
        streamingBlob: StreamingBlob

        // Documents can be bool, string, numbers, an empty list, or an empty map.
        // see: https://smithy.io/2.0/spec/type-refinement-traits.html#default-value-constraints
        @default(true)
        boolDoc: Document

        @default("string")
        stringDoc: Document

        @default(1)
        numberDoc: Document

        @default(1.2)
        floatingPointnumberDoc: Document

        @default([])
        listDoc: Document

        @default({})
        mapDoc: Document

        @default([])
        list: ListOfString

        @default({})
        map: StringStringMap

        @default("1985-04-12T23:20:50.52Z")
        timestamp: Timestamp

        @default("A")
        enum: NestedEnum

        @default(1)
        intEnum: NestedIntEnum
    }
}
