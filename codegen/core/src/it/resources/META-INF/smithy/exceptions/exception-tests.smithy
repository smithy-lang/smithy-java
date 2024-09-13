$version: "2"

namespace smithy.java.codegen.test.exceptions

resource ExceptionTests {
    operations: [
        Exceptions
    ]
}

operation Exceptions {
    errors: [
        ExceptionWithExtraStringException
        SimpleException
        EmptyException
        OptionalMessageException
    ]
}

@error("server")
structure ExceptionWithExtraStringException {
    @required
    message: String

    extra: String
}

@error("server")
structure SimpleException {
    @required
    message: String
}

@error("server")
structure OptionalMessageException {
    message: String
}

@error("server")
structure EmptyException {}
