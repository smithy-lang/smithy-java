$version: "2"

namespace smithy.java.codegen.test.exceptions

@error("server")
structure ExceptionWithExtraStringException {
    @required
    message: String
    extra: String
}
