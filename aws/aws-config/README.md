# AWS Config

Parses AWS shared configuration files (`~/.aws/config` and `~/.aws/credentials`) and resolves credentials from profiles.

## Dependency

```kotlin
dependencies {
    implementation("software.amazon.smithy.java:aws-config:1.1.0")
}
```

## Usage

Config file-based credential resolution is wired up automatically when
`AwsCredentialChainPlugin` is installed on a client. This module registers
itself in the `SHARED_CONFIG` chain slot via ServiceLoader, so no additional
code is needed beyond adding the dependency.

## Programmatic config file support

You can load and query config files directly:

```java
// Load and query the config file
AwsProfileFile file = AwsProfileFile.load();
AwsProfile profile = file.profile("default");
String region = profile.property("region");

// Resolve credentials from a profile directly
var resolver = ProfileIdentityResolver.builder(AwsCredentialsIdentity.class)
    .profileName("dev")
    .build();

IdentityResult<AwsCredentialsIdentity> result = resolver.resolveIdentity(Context.empty());
```

## Supported credential sources

Profiles can define credentials in multiple ways. This module handles:

- **Static keys** — `aws_access_key_id` + `aws_secret_access_key`
- **Session keys** — static keys + `aws_session_token`
- **credential_process** — external program that outputs JSON credentials

## Modular credential sources

Additional sources like IMDS, AssumeRole, SSO, WebIdentity, and Login are
detected and typed but require separate handler modules (`aws-credentials-sts`,
`aws-credentials-sso`, etc.) to resolve. When possible, this module detects
if you intended to use one of these providers but are missing the dependency.

## Extensibility

Implement `AwsConfigCredentialSourceHandler` and register via
`META-INF/services` to handle additional source types.
