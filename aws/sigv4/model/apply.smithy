$version: "2"

namespace aws.auth

use smithy.framework#addsImplicitErrors

apply sigv4 @addsImplicitErrors([InvalidSignatureException])

@error("client")
@httpError(403)
structure InvalidSignatureException {
    message: String
}
