$version: "2"

namespace smithy.java.codegen.test.structures

operation BigDecimalMembers {
    input := {
        @required
        requiredBigDecimal: BigDecimal

        optionalBigDecimal: BigDecimal

        @default(1.0)
        defaultBigDecimal: BigDecimal
    }
}
