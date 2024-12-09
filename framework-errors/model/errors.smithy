$version: "2"

namespace smithy.framework

// TODO: This model should be upstreamed to a package in the main smithy repo
/// Adds an extensible document to framework errors so users can add additional info.
@mixin
structure ErrorInfo {
    info: Document
}

/// InternalFailureException is the catch-all exception for unexpected errors.
/// It indicates either a bug in the framework or an exception being thrown that
/// is not modeled. The service SDK will never include any details about internal
/// failures, such as a meaningful exception message, to the caller, in order to
/// prevent unintended information disclosure.
@error("server")
@httpError(500)
structure InternalFailureException {
    @default("Internal Service Exception")
    message: String
}

/// MalformedRequestException is thrown by the server when a request is unparseable,
/// or if there is a type mismatch between a member in the serialized request and
/// the member in the Smithy model. Since these failures occur during the
/// deserialization process, server developers have no ability to customize these
/// messages, and they will short-circuit request processing before validation
/// occurs.
@error("client")
@httpError(400)
structure MalformedRequestException with [ErrorInfo] {
    message: String
}

/// UnknownOperationException is returned when the request cannot be matched to a
/// service and operation known to the server. This can happen either because the
/// service does not recognize the request protocol, which is common for
/// internet-facing endpoints that receive robotic traffic, or if the request is in
/// a known protocol but for an operation that is unknown to the handler. The latter
/// case can indicate a misconfiguration, such as an operation-level handler being used
/// incorrectly.
@error("client")
@httpError(404)
structure UnknownOperationException with [ErrorInfo] {
    message: String
}

/// AccessDeniedException is thrown to indicate that the client was denied access
/// to a route or resource. Unlike NotAuthorizedException, this exception does not
/// indicate that credentials are missing from the request.
@error("client")
@httpError(403)
structure AccessDeniedException with [ErrorInfo] {
    message: String
}

/// NotAuthorizedException is thrown when a client calls a route that requires
/// authentication, but does not provide credentials for authorization. This is distinct
/// from AccessDeniedException that indicates that credentials were provided but
/// did not have acceptable permissions for the requested operation.
@error("client")
@httpError(401)
structure NotAuthorizedException with [ErrorInfo] {
    message: String
}

/// ThrottlingException is thrown when the service wants a client to reduce its request
/// rate. The most common source of throttling errors is a client exceeding either
/// an overall API rate limit or a specific route rate limit. Throttling exception are
/// retryable. If a `retryAt` Timestamp is provided, clients should retry their request at
/// that time, otherwise they should fall back to their default retry strategy.
@error("client")
@retryable(throttling: true)
@httpError(429)
structure ThrottlingException with [ErrorInfo] {
    message: String
    retryAt: Timestamp
}
