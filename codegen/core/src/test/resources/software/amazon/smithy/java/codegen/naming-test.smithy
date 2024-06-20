$version: "2"

namespace smithy.java.codegen

service TestService {
    version: "today"
    operations: [
        NamingConflicts
        ReservedWordMembers
        ReservedWordShape
        Casing
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

operation Casing {
    input:= {
        snake_case_member: String
        snakeCaseShape: Snake_Case_Shape
        upperSnakeCaseShape: UPPER_SNAKE_CASE_SHAPE
        ACRONYM_Inside_Member: String
        acronymInsideStruct: ACRONYMInsideStruct
        enums: EnumCasing
    }
}

@private
structure Snake_Case_Shape {
}

@private
structure UPPER_SNAKE_CASE_SHAPE {
}

@private
structure ACRONYMInsideStruct {
}

@private
enum EnumCasing {
    camelCase
    snake_case
    PascalCase
    with_1_number
}
