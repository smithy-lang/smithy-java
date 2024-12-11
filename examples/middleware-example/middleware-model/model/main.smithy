$version: "2"

namespace smithy.example.middleware

use smithy.framework#implicitErrors

@implicitErrors([CustomAuthException, RefreshCredentialsException])
@authDefinition
@trait(selector: "service")
structure customAuth {}

// Generic Auth exception for the CustomAuth auth scheme
@error("client")
structure CustomAuthException {
    message: String
}

/// Indicates that client should ref
@error("client")
@retryable
structure RefreshCredentialsException {
    message: String
}
