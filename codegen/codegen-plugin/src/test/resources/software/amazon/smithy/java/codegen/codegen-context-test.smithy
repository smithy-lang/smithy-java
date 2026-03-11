$version: "2"

namespace smithy.java.codegen

@protocolDefinition(
    traits: [timestampFormat, cors, endpoint, hostLabel, http]
)
@trait(selector: "service")
structure testProtocol {}

@authDefinition(
    traits: [httpPayload]
)
@trait(selector: "service")
structure authScheme1 {}

@authDefinition(
    traits: [httpQuery]
)
@trait(selector: "service")
structure authScheme2 {}

@trait(selector: "structure")
structure selectedTrait {}

@testProtocol
@authScheme1
@authScheme2
service TestService {
    version: "today"
    errors: [
        ExampleError
    ]
}

@error("client")
@httpError(401)
@selectedTrait
structure ExampleError {
    message: String
}

service NoProtocolService { version: "today" }
