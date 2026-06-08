# AWS Config

> [!WARNING]
> This is a developer-preview module and may contain bugs. No guarantee is made about API stability.

Provides credential resolution from AWS shared configuration files (`~/.aws/config` and `~/.aws/credentials`). Ships handlers for static keys, session keys, and credential_process.

## Dependency

```kotlin
dependencies {
    implementation("software.amazon.smithy.java:aws-config:1.1.0")
}
```

## Usage

Config file-based credential resolution is wired up automatically when `AwsCredentialChainPlugin` is installed on a client. This module registers its `ChainIdentityProvider` implementations via ServiceLoader, so no additional code is needed beyond adding the dependency.

## Programmatic config file support

You can load and query config files directly:

```java
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

This module handles the following profile credential sources via `ChainIdentityProvider.resolve()`:

- **Static keys** — `aws_access_key_id` + `aws_secret_access_key`
- **Session keys** — static keys + `aws_session_token`
- **credential_process** — external program that outputs JSON credentials

## Modular credential sources

Additional sources (AssumeRole, SSO, WebIdentity, Login) are detected and typed by the chain module but require separate provider modules (`aws-credentials-sts`, `aws-credentials-sso`, etc.) to resolve. When a source is detected but no provider claims it, an actionable error names the missing dependency.

## Extensibility

Implement `ChainIdentityProvider.resolve()` in a new module and register via `META-INF/services/software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider` to handle additional config-file source types.
