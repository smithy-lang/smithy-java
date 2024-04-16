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

@externalDocumentation(example: "https://www.example.com")
list ListOfString {
    @length(min: 1, max: 2)
    member: String
}

@uniqueItems
list SetOfString {
    member: StringMember
}

@references([
    {
        resource: "smithy.example#a"
    }
    {
        resource: "smithy.example#b"
    }
])
string StringMember
