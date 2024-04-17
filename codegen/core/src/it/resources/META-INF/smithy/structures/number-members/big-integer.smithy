$version: "2"

namespace smithy.java.codegen.test.structures.members

operation BigIntegers {
    input := {
        @required
        requiredBigInteger: BigInteger

        optionalBigInteger: BigInteger

        @default(1)
        defaultBigInteger: BigInteger
    }
}
