$version: "2"

namespace software.amazon.smithy.java.json.codegen.test

structure SimpleStruct {
    @required
    name: String

    @required
    age: Integer

    nickname: String

    score: Double
}

structure ListStruct {
    @required
    names: StringList

    counts: IntegerList
}

list StringList {
    member: String
}

list IntegerList {
    member: Integer
}

structure MapStruct {
    @required
    tags: StringMap
}

map StringMap {
    key: String
    value: String
}

structure FullStruct {
    @required
    name: String

    @required
    age: Integer

    @required
    active: Boolean

    score: Double

    count: Long

    rating: Float

    data: Blob

    @timestampFormat("date-time")
    createdAt: Timestamp

    bigNum: BigInteger

    precise: BigDecimal

    tags: StringList

    metadata: StringMap
}
