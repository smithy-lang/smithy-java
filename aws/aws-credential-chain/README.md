# AWS Credential Chain

Assembles an ordered AWS credential provider chain from SPI-discovered
providers. Provides the infrastructure for modular credential resolution.

## Dependency

```kotlin
dependencies {
    implementation("software.amazon.smithy.java:aws-credential-chain:1.1.0")
}
```

## Wiring to a client

### Automatic (codegen)

Services modeled with `@aws.auth#sigv4` or `@aws.auth#sigv4a` automatically get
`AwsCredentialChainPlugin` added as a default plugin during code generation.
No manual wiring is needed: just add the provider modules you need to your
runtime dependencies.

### Manual

```java
var client = MyClient.builder()
    .addPlugin(new AwsCredentialChainPlugin())
    .build();
```

The plugin registers the credential chain as the client's identity resolver and
adds an interceptor that invalidates cached credentials on auth failures
(`ExpiredToken`, `InvalidToken`, `AuthFailure`).

## Standalone usage

```java
try (AwsCredentialChain chain = AwsCredentialChain.create()) {
    IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(context);
}
```

## How it works

The chain discovers `AwsCredentialProvider` implementations via ServiceLoader, 
orders them by builtin enum slots, and tries each in order until one succeeds.

## Builtin slots (in priority order)

1. `CODE` — programmatic
2. `JAVA_SYSTEM_PROPERTIES` — `aws.accessKeyId`
3. `ENVIRONMENT` — `AWS_ACCESS_KEY_ID`
4. `WEB_IDENTITY_TOKEN_ENV` — `AWS_WEB_IDENTITY_TOKEN_FILE` + `AWS_ROLE_ARN`
5. `SHARED_CONFIG` — `~/.aws/config` / `~/.aws/credentials`
6. `ECS_CONTAINER` — `AWS_CONTAINER_CREDENTIALS_FULL_URI`
7. `EC2_INSTANCE_METADATA` — IMDS

## Ordering

Providers position themselves relative to builtin slots using
`OrderingConstraint`:

- `Builtin(slot)` — claims a builtin slot (one provider per slot). Only one
  provider can claim a builtin. Not all builtins have to be claimed.
- `Before(slot)` — inserts before the given slot's position
- `After(slot)` — inserts after the given slot's position

Before/After reference enum values only, so cycles are impossible. If the
referenced slot has no registered provider, the custom provider is placed
where that slot would be in enum order.

## Adding a custom provider

```java
public class MyProvider implements AwsCredentialProvider {
    @Override
    public String name() {
        return "MyCustomProvider";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.After(BuiltinProvider.SHARED_CONFIG);
    }

    @Override
    public IdentityResolver<AwsCredentialsIdentity> create(ProviderContext ctx) {
        return new MyResolver();
    }
}
```

Register in `META-INF/services/software.amazon.smithy.java.aws.credentials.chain.AwsCredentialProvider`.
