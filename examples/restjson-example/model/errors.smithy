$version: "2"

namespace smithy.example

@error("server")
structure ValidationError {
    @required
    message: String
}
