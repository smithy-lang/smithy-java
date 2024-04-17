$version: "2"

namespace smithy.java.codegen.test.structures.members

operation BigDecimals {
    input := {
        @required
        requiredBigDecimal: BigDecimal

        optionalBigDecimal: BigDecimal

        @default(1.0)
        defaultBigDecimal: BigDecimal
    }
}
