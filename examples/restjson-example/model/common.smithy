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

list ListOfString {
    member: String
}

@uniqueItems
list SetOfString {
    member: String
}
