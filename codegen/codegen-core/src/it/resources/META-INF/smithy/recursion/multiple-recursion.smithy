$version: "2"

namespace smithy.java.codegen.test.recursion

operation MultipleRecursion {
    input := {
        attributeValue: AttributeValue
    }
}

union AttributeValue {
    M: MapAttributeValue
    L: ListAttributeValue
}

map MapAttributeValue {
    key: String
    value: AttributeValue
}

list ListAttributeValue {
    member: AttributeValue
}
