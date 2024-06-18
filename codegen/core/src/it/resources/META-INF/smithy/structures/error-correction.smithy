$version: "2"

namespace smithy.java.codegen.test.structures

use smithy.java.codegen.test.common#ListOfString
use smithy.java.codegen.test.common#NestedEnum
use smithy.java.codegen.test.common#NestedIntEnum
use smithy.java.codegen.test.common#StreamingBlob
use smithy.java.codegen.test.common#StringStringMap

/// Checks that client error correction is correctly performed
/// see: https://smithy.io/2.0/spec/aggregate-types.html#client-error-correction
operation ClientErrorCorrection {
    input := {
        @required
        boolean: Boolean

        @required
        bigDecimal: BigDecimal

        @required
        bigInteger: BigInteger

        @required
        byte: Byte

        @required
        double: Double

        @required
        float: Float

        @required
        integer: Integer

        @required
        long: Long

        @required
        short: Short

        @required
        string: String

        @required
        blob: Blob

        @required
        streamingBlob: StreamingBlob

        @required
        document: Document

        @required
        list: ListOfString

        @required
        map: StringStringMap

        @required
        timestamp: Timestamp

        @required
        enum: NestedEnum

        @required
        intEnum: NestedIntEnum
    }
}
