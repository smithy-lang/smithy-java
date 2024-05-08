$version: "2"

namespace smithy.java.codegen

service TestService {
    version: "today"
    operations: [
        NamingConflicts
        ReservedWordMembers
        ReservedWordShape
    ]
}

/// The names of shapes in this operation conflict with
/// base Java shapes like `Map` and so must be disambiguated
operation NamingConflicts {
    input := {
        // Structure named map that will conflict with java Map
        map: Map
        // Field that will pull in java map
        javaMap: StringStringMap
        // Structure named list
        list: List
        // Field that will pull in java list
        javaList: StringList
    }
}

@private
structure Map {
    field: String
}

@private
structure List {
    member: String
}

@private
map StringStringMap {
    key: String
    value: String
}

@private
list StringList {
    member: String
}

/// The names of these members conflict with reserved Java keywords
operation ReservedWordMembers {
    input := {
        byte: Byte
        static: String
        double: Double
    }
}

operation ReservedWordShape {
    input := {
        reserved: Builder
    }
}

/// The name of this shape conflicts with the static `Builder` class added to
/// all code generated structures
@private
structure Builder {
    field: String
}
