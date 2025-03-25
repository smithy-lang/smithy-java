$version: "2"

namespace smithy.protocols

structure JsonRpcRequest {
    @required
    jsonrpc: String

    @required
    method: String

    @required
    id: Integer

    params: Document
}

structure JsonRpcResponse {
    @required
    jsonrpc: String

    result: Document

    error: JsonRpcErrorResponse

    @required
    id: PrimitiveInteger = 0
}

structure JsonRpcErrorResponse {
    @required
    code: PrimitiveInteger = 0

    message: String

    data: Document
}

@trait(selector: "service")
structure jsonRpc2 {}

@trait(selector: "operation")
structure jsonRpc2Method {
    @required
    method: NonEmptyString
}

@private
@length(min: 1)
string NonEmptyString

// TODO: only select errors
@trait(selector: "structure")
structure jsonRpc2Error {
    @required
    code: Integer
}
