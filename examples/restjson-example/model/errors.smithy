$version: "2"

namespace smithy.example

@error("client")
structure ValidationError {
    @required
    message: String
}
