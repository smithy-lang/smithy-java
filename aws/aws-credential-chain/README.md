# Credential Chain

Assembles an ordered identity provider chain from SPI-discovered providers.
Supports any identity type (AWS credentials, bearer tokens, etc.) through a
single generic chain.

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
No manual wiring needed - just add the provider modules you need to your
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
try (var chain = CredentialChain.create(AwsCredentialsIdentity.class)) {
    IdentityResult<AwsCredentialsIdentity> result = chain.resolveIdentity(context);
}
```

## How it works

The chain discovers `ChainIdentityProvider` implementations via ServiceLoader.
Each provider's `create(Class<I> identityType, ProviderContext context)` is
called with the requested identity type. Providers that don't support the type
return `null` and are skipped. The chain tries remaining providers in order
until one succeeds.

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

- `Builtin(slot)` — claims a builtin slot (one provider per slot)
- `Before(slot)` — inserts before the given slot's position
- `After(slot)` — inserts after the given slot's position

Before/After reference enum values only, so cycles are impossible. If the
referenced slot has no registered provider, the custom provider is placed where
that slot would be in enum order.

## Adding a custom provider

```java
public class MyProvider implements ChainIdentityProvider {
    @Override
    public String name() {
        return "MyCustomProvider";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.After(BuiltinProvider.SHARED_CONFIG);
    }

    @Override
    public <I extends Identity> IdentityResolver<I> create(Class<I> identityType, ProviderContext ctx) {
        if (identityType == AwsCredentialsIdentity.class) {
            return (IdentityResolver<I>) new MyResolver();
        }
        return null;
    }
}
```

Register in `META-INF/services/software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider`.
