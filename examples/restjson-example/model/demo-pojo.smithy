$version: "2"

namespace smithy.example

/// This Pojo has some documentation attached
structure DemoPojo {
    /// This member shows how documentation traits work
    @unstable
    @deprecated(since: "sometime")
    @externalDocumentation("Puffins are still cool": "https://en.wikipedia.org/wiki/Puffin")
    @since("4.5")
    documentation: String

    @required
    requiredPrimitive: Integer

    optionalPrimitive: Integer

    @default(1)
    defaultPrimitive: Integer

    @required
    requiredList: ListOfString

    optionalList: ListOfString

    /// NOTE: List and Map defaults can ONLY be empty
    @default([])
    defaultList: ListOfString

    @required
    requiredSet: SetOfString

    optionalSet: SetOfString

    @required
    requiredMap: MapOfIntegers

    @default({})
    defaultMap: MapOfIntegers

    optionalMap: MapOfIntegers

    @jsonName("Age")
    stringWithJsonName: String

    @default("1985-04-12T23:20:50.52Z")
    defaultTimestamp: Timestamp

    nestedStruct: Nested
}

map MapOfIntegers {
    key: String
    value: Integer
}

structure Nested {
    fieldA: String
}
