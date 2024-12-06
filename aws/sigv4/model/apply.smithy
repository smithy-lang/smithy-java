$version: "2"

namespace aws.auth

use smithy.framework#implicitErrors

apply sigv4 @implicitErrors([InvalidSignatureException])

@error("client")
@httpError(403)
structure InvalidSignatureException {
    message: String
}
