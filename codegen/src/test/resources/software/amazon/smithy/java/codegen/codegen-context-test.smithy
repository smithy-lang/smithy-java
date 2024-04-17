$version: "2"

namespace smithy.java.codegen

@protocolDefinition(
    traits: [
        timestampFormat
        cors
        endpoint
        hostLabel
        http
    ]
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

@testProtocol
@authScheme1
@authScheme2
service TestService {
    version: "today"
}

service NoProtocolService {
    version: "today"
}
