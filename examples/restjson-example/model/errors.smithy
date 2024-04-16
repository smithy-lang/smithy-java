$version: "2"

namespace smithy.example

@error("client")
@httpError(403)
structure ValidationError {
    @required
    message: String
}
