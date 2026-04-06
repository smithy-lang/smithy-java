$version: "2"

namespace software.amazon.smithy.modelbundle.api

structure SmithyBundle {
    /// unique identifier for the configuration type. used to resolve the appropriate Bundler.
    @required
    configType: String

    /// fully-qualified ShapeId of the service
    @required
    serviceName: String

    /// Bundle-specific configuration. If this bundle does not require configuration, this
    /// field may be omitted.
    config: Document

    /// Bundle-specific metadata. If this bundle does not require any additional metadata, this
    /// field may be omitted.
    metadata: Document

    /// model that describes the service. The service given in `serviceName` must be present.
    @required
    model: SmithyModel

    /// model describing the generic arguments that must be present in every request. If this
    /// bundle does not require generic arguments, this field may be omitted.
    additionalInput: AdditionalInput

    /// Version of the model bundle format. Determines how additionalInput is handled.
    /// If not specified, defaults to V1 (legacy) behavior.
    modelBundleVersion: ModelBundleVersion
}

string SmithyModel

structure AdditionalInput {
    @required
    identifier: String

    @required
    model: SmithyModel
}

/// Version of the model bundle format. Used to determine input handling behavior.
enum ModelBundleVersion {
    /// Mix additionalInput into existing input shape (legacy behavior)
    V1

    /// Wrap input in new structure containing both original input and additionalInput
    V2
}
