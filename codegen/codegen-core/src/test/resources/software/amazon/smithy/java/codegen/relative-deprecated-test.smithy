$version: "2"

namespace smithy.java.codegen

service TestService {
    version: "today"
    operations: [
        DeprecatedOperationDate
        NotYetDeprecatedDate
        DeprecatedOperationVersion
        NotYetDeprecatedVersion
    ]
}

/// This operation is deprecated long before the time
/// the `relativeDate` sets and so should be filtered out.
@deprecated(since: "1792-01-01")
operation DeprecatedOperationDate {
    input := {
        value: String
    }
}

/// This operation is deprecated after the time
/// the `relativeDate` sets and so should NOT be filtered out.
@deprecated(since: "2025-01-01")
operation NotYetDeprecatedDate {
    input := {
        value: String
    }
}

/// This operation is deprecated before the version
/// the `relativeVersion` sets and so should be filtered out.
@deprecated(since: "1.0.1")
operation DeprecatedOperationVersion {
    input := {
        value: String
    }
}

/// This operation is deprecated after the version
/// the `relativeVersion` sets and so should NOT be filtered out.
@deprecated(since: "1.1.1")
operation NotYetDeprecatedVersion {
    input := {
        value: String
    }
}
