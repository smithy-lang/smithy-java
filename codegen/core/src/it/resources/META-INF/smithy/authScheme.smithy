$version: "2"

namespace smithy.java.codegen.test

operation AllAuth {
    input := {
        string: String
    }
}

@auth([httpBasicAuth])
operation ScopedAuth {
    input := {
        string: String
    }
}

@auth([])
operation NoAuth {
    input := {
        string: String
    }
}
