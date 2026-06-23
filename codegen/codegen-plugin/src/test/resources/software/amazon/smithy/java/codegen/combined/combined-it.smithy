$version: "2"

metadata shapeClosures = [
    {
        // Includes the primary service (so it satisfies combined mode) plus the unconnected
        // standalone type. Used to verify driving combined mode from a pre-authored closure.
        id: "smithy.java.codegen.combined.it#combinedClosure"
        includeBySelector: ":is([id = 'smithy.java.codegen.combined.it#CombinedItService'], [id = 'smithy.java.codegen.combined.it#StandaloneType'])"
        rename: {
            "smithy.java.codegen.combined.it#Widget": "Gadget"
        }
    }
    {
        // Includes the primary service plus shapes not bound to it: a standalone operation and a
        // standalone resource. Used to verify that combined mode skips operations and resources
        // outside the primary service's closure.
        id: "smithy.java.codegen.combined.it#closureWithUnboundShapes"
        includeBySelector: ":is([id = 'smithy.java.codegen.combined.it#CombinedItService'], [id = 'smithy.java.codegen.combined.it#OrphanOp'], [id = 'smithy.java.codegen.combined.it#OrphanResource'])"
    }
]

namespace smithy.java.codegen.combined.it

@protocolDefinition(
    traits: [timestampFormat, cors, endpoint, hostLabel, http]
)
@trait(selector: "service")
structure testProtocol {}

/// Service used to verify combined TYPES + CLIENT generation compiles and runs: the service
/// closure (with a rename applied) plus an unconnected standalone type are generated together.
@testProtocol
service CombinedItService {
    version: "today"
    operations: [
        GetThing
    ]
    rename: {
        "smithy.java.codegen.combined.it#Widget": "Gadget"
    }
}

operation GetThing {
    input := {
        id: String
    }
    output := {
        widget: Widget
    }
}

/// Connected to the service through an operation and renamed to Gadget; exercises rename
/// threading end to end (the generated class must be named Gadget, not Widget).
structure Widget {
    name: String
}

/// Not reachable from the service. In combined mode this must still be generated.
structure StandaloneType {
    value: String
}

/// An operation not bound to the primary service. A closure can pull it in, but combined mode must
/// not generate it until multi-service generation is supported.
operation OrphanOp {
    input := {
        value: String
    }
    output := {
        result: String
    }
}

/// A resource not bound to the primary service. A closure can pull it in, but combined mode must
/// not generate it until multi-service generation is supported.
resource OrphanResource {
    identifiers: { id: String }
}
