$version: "2"

namespace smithy.example

@streaming
blob Stream

@sensitive
@timestampFormat("date-time")
timestamp Birthday

map MapListString {
    key: String
    value: ListOfString
}

map MapStringString {
    key: String
    value: String
}

list ListOfString {
    @length(min: 1)
    member: String
}

@uniqueItems
list SetOfString {
    member: String
}

structure Nested {
    fieldA: String
    fieldB: Integer
}

map MapOfStringMap {
    key: String
    value: MapStringString
}

list ListOfStringList {
    member: ListOfString
}
