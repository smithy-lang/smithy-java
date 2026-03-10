$version: "2"

namespace smithy.test

/// All members in this shape use primitive java types
structure PrimitivesNotNullable {
    @required
    byte: Byte

    @required
    short: Short

    @required
    int: Integer

    @required
    long: Long

    @required
    float: Float

    @required
    double: Double

    @required
    boolean: Boolean
}

/// All members in this shape use boxed java types
structure PrimitivesNullable {
    byte: Byte
    short: Short
    int: Integer
    long: Long
    float: Float
    double: Double
    boolean: Boolean
}
