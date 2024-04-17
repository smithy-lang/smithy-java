$version: "2"

namespace smithy.java.codegen.test.exceptions


@error("server")
structure SimpleException {
    @required
    message: String
}
