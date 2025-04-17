$version: "2"

namespace software.amazon.smithy.awsmcp

/// Describes a wrapper around a single AWS service call. It contains a region,
/// the AWS credentials profile that will be used to sign the request, and the
/// request payload.
structure PreRequest {
    /// The region that the request will be made in. Examples: us-east-1, ap-northeast-2
    awsRegion: String = "us-east-1"

    /// The name of the AWS profile that will provide credentials
    /// for the request.
    awsProfileName: String = "default"
}

structure AwsServiceMetadata {
    @required
    serviceName: String

    @required
    sigv4SigningName: String

    @required
    endpoints: Endpoints
}

map Endpoints {
    key: String
    value: String
}
