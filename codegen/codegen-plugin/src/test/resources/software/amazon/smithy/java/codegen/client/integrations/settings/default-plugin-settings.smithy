$version: "2"

namespace smithy.java.codegen.settings

service DefaultPluginSettingsService {
    operations: [
        NoOpOperation
    ]
}

operation NoOpOperation {
    input := {}
    output := {}
}
