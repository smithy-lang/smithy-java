$version: "2"

namespace com.smithy.validation

structure Person {
    @required
    @length(max: 7)
    name: String

    favoriteColor: String

    @range(max: 150)
    age: Integer

    birthday: Timestamp

    binary: Blob

    queryParams: StringToStringList

    parents: Parents
}

map StringToStringList {
    key: String
    value: StringList
}

list StringList {
    member: String
}

structure Parents {
    father: Person
    mother: Person
}
