$version: "2"

namespace smithy.java.codegen.test

use smithy.java.codegen.test.enums#EnumTests
use smithy.java.codegen.test.exceptions#ExceptionTests
use smithy.java.codegen.test.lists#ListTests
use smithy.java.codegen.test.maps#MapTests
use smithy.java.codegen.test.naming#Naming
use smithy.java.codegen.test.structures#StructureTests
use smithy.java.codegen.test.unions#UnionTests

service TestService {
    version: "today"
    resources: [
        EnumTests
        ExceptionTests
        ListTests
        MapTests
        // TODO: Add recursion tests
        StructureTests
        UnionTests
    ]
    operations: [
        Naming
    ]
}
