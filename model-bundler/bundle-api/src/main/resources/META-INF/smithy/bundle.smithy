$version: "2"

namespace software.amazon.smithy.modelbundle.api

structure Bundle {
    /// unique identifier for the configuration type. used to resolve the appropriate Bundler.
    @required
    configType: String

    /// fully-qualified ShapeId of the service
    @required
    serviceName: String

    /// Bundle-specific configuration. If this bundle does not require configuration, this
    /// field may be omitted.
    config: Document

    /// model that describes the service. The service given in `serviceName` must be present.
    @required
    model: Model

    /// model describing the generic arguments that must be present in every request. If this
    /// bundle does not require generic arguments, this field may be omitted.
    requestArguments: GenericArguments
}

union Model {
    smithyModel: String
}

structure GenericArguments {
    @required
    identifier: String

    @required
    model: Model
}
