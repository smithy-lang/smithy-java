$version: "2"

namespace smithy.java.codegen.test.structures.defaults

use smithy.java.codegen.test.structures.members#Documents

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
        streamingBlob: Streaming

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
        list: DefaultList

        @default({})
        map: DefaultMap

        @default("1985-04-12T23:20:50.52Z")
        timestamp: Timestamp

        @default("fish")
        enum: FishOrBird

        @default(1)
        intEnum: OneOrTwo
    }
}

@private
list DefaultList {
    member: String
}

@private
map DefaultMap {
    key: String
    value: String
}

@private
enum FishOrBird {
    BIRD = "bird"
    FISH = "fish"
}

@private
intEnum OneOrTwo {
    ONE = 1
    TWO = 2
}

@private
@streaming
blob Streaming
