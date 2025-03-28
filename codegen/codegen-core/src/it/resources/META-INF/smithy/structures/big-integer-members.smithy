$version: "2"

namespace smithy.java.codegen.test.structures

operation BigIntegerMembers {
    input := {
        @required
        requiredBigInteger: BigInteger

        optionalBigInteger: BigInteger

        @default(1)
        defaultBigInteger: BigInteger
    }
}
