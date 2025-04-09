$version: "2"

namespace software.amazon.smithy.awsmcp

/// Describes a wrapper around a single AWS service call. It contains a region,
/// the AWS credentials profile that will be used to sign the request, and the
/// request payload.
structure PreRequest {
    /// The region that the request will be made in. Examples: us-east-1, ap-northeast-2
    @required
    awsRegion: String = "us-east-1"

    /// The name of the AWS profile that will provide credentials
    /// for the request.
    @required
    awsProfileName: String = "default"
}

structure AwsServiceMetadata {
    @required
    serviceName: String

    @required
    sigv4SigningName: String

    @required
    model: Model

    @required
    endpoints: Endpoints
}

union Model {
    /// a literal smithy model
    model: String

    /// a reference to another file in this bundle
    modelRef: String
}

map Endpoints {
    key: String
    value: String
}
