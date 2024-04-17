$version: "2"

namespace smithy.java.codegen.test.exceptions

operation ExceptionOperation {
    errors: [
        ExceptionWithExtraStringException
        SimpleException
    ]
}
