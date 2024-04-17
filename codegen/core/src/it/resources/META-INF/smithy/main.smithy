$version: "2"

namespace smithy.java.codegen.test

use smithy.java.codegen.test.structures#StructureShapes

service TestService {
    version: "today"
    resources: [
        StructureShapes
    ]
}
