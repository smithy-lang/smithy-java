$version: "2"

namespace smithy.java.codegen.test.structures.members

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
        streamingBlob: NestedStreamingBlob

        @required
        document: Document

        @required
        list: CorrectedList

        @required
        map: CorrectedMap

        @required
        structure: CouldBeEmptyStruct

        @required
        timestamp: Timestamp
    }
}

@private
@streaming
blob NestedStreamingBlob

@private
list CorrectedList {
    member: String
}

@private
map CorrectedMap {
    key: String
    value: String
}

@private
structure CouldBeEmptyStruct {
    fieldA: String
}
