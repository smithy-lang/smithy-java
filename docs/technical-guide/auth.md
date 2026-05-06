# Auth System

> **Last updated:** April 29, 2026

The auth system provides pluggable authentication and signing for Smithy-Java clients. It's layered across four modules:
shared identity abstractions, client-side auth scheme resolution, AWS-specific identity types, and the SigV4 signing
implementation.

**Source:**
- [`auth-api/`](https://github.com/smithy-lang/smithy-java/tree/main/auth-api) — Shared abstractions
- [`client/client-auth-api/`](https://github.com/smithy-lang/smithy-java/tree/main/client/client-auth-api) — Client auth scheme resolution
- [`aws/aws-auth-api/`](https://github.com/smithy-lang/smithy-java/tree/main/aws/aws-auth-api) — AWS identity types
- [`aws/aws-sigv4/`](https://github.com/smithy-lang/smithy-java/tree/main/aws/aws-sigv4) — SigV4 implementation

## Core Abstractions (`auth-api`)

### Identity

```java
public interface Identity {
    default Instant expirationTime() { return null; }
}
```

Built-in identity types:
- `TokenIdentity` — bearer token (`String token()`)
- `ApiKeyIdentity` — API key (`String apiKey()`)
- `LoginIdentity` — username/password

### IdentityResolver

```java
public interface IdentityResolver<IdentityT extends Identity> {
    IdentityResult<IdentityT> resolveIdentity(Context requestProperties);
    Class<IdentityT> identityType();
    static <I extends Identity> IdentityResolver<I> chain(List<IdentityResolver<I>> resolvers);
    static <I extends Identity> IdentityResolver<I> of(I identity);  // static resolver
}
```

Returns `IdentityResult<T>` (success-or-error wrapper) instead of throwing. Expected failures (missing env vars) return
`IdentityResult.ofError()`. `IdentityResolverChain` tries resolvers in order, returns first success.

### IdentityResolvers (Registry)

```java
public interface IdentityResolvers {
    <T extends Identity> IdentityResolver<T> identityResolver(Class<T> identityClass);
    static IdentityResolvers of(IdentityResolver<?>... resolvers);
}
```

Type-safe registry mapping identity class → resolver. Used by `AuthScheme.identityResolver(resolvers)`.

### Signer

```java
@FunctionalInterface
public interface Signer<RequestT, IdentityT extends Identity> extends AutoCloseable {
    SignResult<RequestT> sign(RequestT request, IdentityT identity, Context properties);
}
```

`SignResult<RequestT>` is a record: `(RequestT signedRequest, String signature)`. The signature string is used as the
seed for event stream signing.

## Client Auth API (`client-auth-api`)

### AuthScheme

```java
public interface AuthScheme<RequestT, IdentityT extends Identity> {
    ShapeId schemeId();
    Class<RequestT> requestClass();
    Class<IdentityT> identityClass();
    default IdentityResolver<IdentityT> identityResolver(IdentityResolvers resolvers);
    default Context getSignerProperties(Context context);
    default Context getIdentityProperties(Context context);
    Signer<RequestT, IdentityT> signer();
    default <F extends Frame<?>> FrameProcessor<F> eventSigner(...);
}
```

An AuthScheme bundles: scheme ID (e.g., `aws.auth#sigv4`), identity resolver lookup, and signer. `getSignerProperties()`
/ `getIdentityProperties()` extract scheme-specific config from the client context.

### AuthSchemeResolver

```java
@FunctionalInterface
public interface AuthSchemeResolver {
    List<AuthSchemeOption> resolveAuthScheme(AuthSchemeResolverParams params);
}
```

`DefaultAuthSchemeResolver` iterates `operation.effectiveAuthSchemes()` and wraps each in an `AuthSchemeOption`. The
client pipeline picks the first option with a matching scheme, compatible request class, and available identity
resolver.

### AuthSchemeFactory (SPI)

```java
public interface AuthSchemeFactory<T extends Trait> {
    ShapeId schemeId();
    AuthScheme<?, ?> createAuthScheme(T trait);
}
```

Discovered via `ServiceLoader`. Receives the Smithy trait instance and creates a configured `AuthScheme`.

## Pipeline Integration

Auth resolution happens in `ClientPipeline.doSendOrRetry()` between the `modifyBeforeSigning` and `readBeforeSigning`
interceptor hooks:

1. Build `AuthSchemeResolverParams` with protocol ID, operation, and context
2. Call `authSchemeResolver.resolveAuthScheme(params)` → priority-ordered `List<AuthSchemeOption>`
3. Iterate options, look up each `schemeId` in `supportedAuthSchemes`
4. Check `authScheme.requestClass().isAssignableFrom(request.getClass())`
5. Merge identity/signer properties from scheme defaults + option overrides
6. Call `authScheme.identityResolver(identityResolvers)` — skip if null
7. Call `resolver.resolveIdentity(identityProperties)`
8. First scheme with a non-null resolver becomes the `ResolvedScheme`
9. After endpoint resolution, apply endpoint auth scheme property overrides
10. `authScheme.signer().sign(request, identity, signerProperties)` → signed request

### Property Layering

Signer properties are merged from three sources (later overrides earlier):
1. Scheme defaults (`authScheme.getSignerProperties(context)`)
2. Resolver overrides (`AuthSchemeOption.signerPropertyOverrides()`)
3. Endpoint overrides (`applyEndpointAuthSchemeOverrides`)

## AWS Auth (`aws-auth-api`)

### AwsCredentialsIdentity

```java
public interface AwsCredentialsIdentity extends Identity {
    String accessKeyId();
    String secretAccessKey();
    default String sessionToken() { return null; }
    default String accountId() { return null; }
}
```

### AwsCredentialsResolver

```java
public interface AwsCredentialsResolver extends IdentityResolver<AwsCredentialsIdentity> {
    @Override default Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }
}
```

## SigV4 Implementation (`aws-sigv4`)

### SigV4AuthScheme

```java
public final class SigV4AuthScheme implements AuthScheme<HttpRequest, AwsCredentialsIdentity> {
    public SigV4AuthScheme(String signingName);
    // schemeId() → "aws.auth#sigv4"
    // getSignerProperties() extracts SIGNING_NAME, REGION, CLOCK from context
    // signer() → SigV4Signer.create()
    // eventSigner() → SigV4EventSigner
}
```

Its inner `Factory` class implements `AuthSchemeFactory<SigV4Trait>` and is registered via SPI.

### SigV4Signer — Signing Flow

1. Extract `region`, `signingName`, `clock` from properties
2. Compute payload hash (SHA-256 hex of body)
3. Build canonical request: method + path + query + sorted headers + signed headers + payload hash
4. Derive signing key: `HMAC(HMAC(HMAC(HMAC("AWS4"+secret, date), region), service), "aws4_request")`
   - Cached in `SigningCache` (bounded LRU, 300 entries, `StampedLock`-protected)
   - Valid for the same calendar day
5. Compute signature: `HMAC(signingKey, stringToSign)`
6. Build `Authorization` header

Performance optimizations:
- `SigningResources` pools `StringBuilder`, `MessageDigest`, and `Mac` instances
- `Pool<T>` uses `ConcurrentLinkedQueue` (32 max)
- `SigningCache` uses `LinkedHashMap` with FIFO eviction and `StampedLock`
- Manual date formatting (avoids `DateTimeFormatter`)

### SigV4EventSigner

Implements chained event stream signing (`AWS4-HMAC-SHA256-PAYLOAD`). Each frame's signature depends on the previous
frame's signature. Produces frames with `:date` and `:chunk-signature` headers. Returns a `closingFrame()` that signs an
empty payload.

## Auth Scheme Discovery

**Generated clients**: Auth schemes are hardcoded by codegen based on `AuthSchemeFactory` SPI.

**Dynamic client**: `SimpleAuthDetectionPlugin` discovers auth schemes at runtime via `ServiceLoader`, reads effective
  auth schemes from the model via `ServiceIndex.getEffectiveAuthSchemes()`, and creates schemes via factories.

## Configuration Points

Users can customize auth at three levels:
1. **Client builder** — `putSupportedAuthSchemes()`, `authSchemeResolver()`, `addIdentityResolver()`
2. **Plugins** — `ClientPlugin.configureClient()` can add schemes, resolvers, identity resolvers
3. **Per-request** — `RequestOverrideConfig` can override auth scheme resolver, add schemes, add identity resolvers
