# AWS Credentials IMDS

Credential provider for EC2 instances using the Instance Metadata Service
(IMDSv2).

## Dependency

```kotlin
dependencies {
    implementation("software.amazon.smithy.java:aws-credentials-imds:1.1.0")
}
```

## Usage

No code changes are needed. The provider is discovered automatically via
ServiceLoader and claims the `EC2_INSTANCE_METADATA` slot in the credential
chain.

## Behavior

- Uses IMDSv2 exclusively (no v1 fallback)
- Tries the extended API first (`/security-credentials-extended/`), falls back
  to legacy on 404
- Caches credentials with background refresh before expiration
- Implements static stability: expired credentials are returned during outages
- Retries with exponential backoff (3 attempts)

## Configuration

Configuration is checked in priority order. The first non-null value wins.

    system property > env var > config file

| Source | Key | Effect |
|--------|-----|--------|
| System property | `aws.disableEc2Metadata=true` | Disables IMDS |
| Environment | `AWS_EC2_METADATA_DISABLED=true` | Disables IMDS |
| Config file | `disable_ec2_metadata=true` | Disables IMDS |
| Environment | `AWS_EC2_METADATA_SERVICE_ENDPOINT` | Overrides endpoint (e.g., for IPv6) |
| System property | `aws.ec2InstanceProfileName` | Skips profile discovery |
| Environment | `AWS_EC2_INSTANCE_PROFILE_NAME` | Skips profile discovery |
| Config file | `ec2_instance_profile_name` | Skips profile discovery |
