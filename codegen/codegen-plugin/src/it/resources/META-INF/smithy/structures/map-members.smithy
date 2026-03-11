$version: "2"

namespace smithy.java.codegen.test.structures

operation MapMembers {
    input := {
        @required
        requiredMap: MapStringString

        optionalMap: MapStringString

        // Map defaults can only be empty
        @default({})
        defaultMap: MapStringString
    }
}

map MapStringString {
    key: String
    value: String
}
