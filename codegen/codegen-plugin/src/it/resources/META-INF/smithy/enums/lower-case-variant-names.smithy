$version: "2"

namespace smithy.java.codegen.test.enums

/// While it is not recommended, some services use lower case names for enum variants.
/// NOTE: This test just ensures that the generated enums are compile-able
operation LowerCaseVariantNames {
    input := {
        enum: LowerCaseVariantsEnum
        intEnum: LowerCaseVariantsIntEnum
    }
}

enum LowerCaseVariantsEnum {
    test = "test"

    test_underscore = "underscore"

    // Uses a normally escaped member name. Because enum members get capitalized, they dont need normal escaping.
    default = "default"
}

intEnum LowerCaseVariantsIntEnum {
    test = 1

    test_underscore = 2

    // Uses a normally escaped member name. Because enum members get capitalized, they dont need normal escaping.
    default = 3
}
