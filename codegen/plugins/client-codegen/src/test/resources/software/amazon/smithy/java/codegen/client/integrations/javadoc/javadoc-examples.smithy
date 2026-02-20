$version: "2"

namespace smithy.java.codegen

service TestService {
    operations: [
        ExamplesOperation
    ]
}

@error("server")
structure ExampleError {
    message: String
}

/// Base docs
@examples([
    {
        title: "Basic Example"
        input: {
            foo: "foo"
        }
        output: {
            bar: "bar"
        }
    }
    {
        title: "Error Example"
        input: {
            foo: "bar"
        }
        error: {
            shapeId: ExampleError
            content: {
                message: "bar"
            }
        }
    }
])
operation ExamplesOperation {
    input := {
        foo: String
    }
    output := {
        bar: String
    }
    errors: [ExampleError]
}
