$version: "2"

namespace smithy.example

@streaming
blob Stream

@sensitive
timestamp Birthday

map MapListString {
    key: String
    value: ListOfString
}

list ListOfString {
    member: String
}
