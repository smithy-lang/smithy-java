/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.CallException;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointAuthScheme;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.datastream.PooledDataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenRequest;
import software.amazon.smithy.java.retries.api.AcquireInitialTokenResponse;
import software.amazon.smithy.java.retries.api.RecordSuccessRequest;
import software.amazon.smithy.java.retries.api.RecordSuccessResponse;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenRequest;
import software.amazon.smithy.java.retries.api.RefreshRetryTokenResponse;
import software.amazon.smithy.java.retries.api.RetryStrategy;
import software.amazon.smithy.java.retries.api.RetryToken;
import software.amazon.smithy.java.retries.api.TokenAcquisitionFailedException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class ClientPipelineTest {

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
                    service Sprockets {
                        operations: [GetSprocket]
                        errors: [ServiceFooError]
                    }

                    @http(method: "POST", uri: "/s")
                    operation GetSprocket {
                        input := {
                            id: String
                        }
                        output := {
                            id: String
                        }
                        errors: [InvalidSprocketId]
                    }

                    @error("client")
                    @httpError(429)
                    @retryable
                    structure InvalidSprocketId {}

                    @error("server")
                    @httpError(500)
                    structure ServiceFooError {}
                    """)
            .discoverModels()
            .assemble()
            .unwrap();

    @Test
    public void canShortCircuitRequests() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .protocol(new RestJsonClientProtocol(service))
                .transport(new JavaHttpClientTransport())
                .endpointResolver(EndpointResolver.staticEndpoint("https://localhost:8081"))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .retryStrategy(new RetryStrategy() {
                    @Override
                    public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
                        throw new TokenAcquisitionFailedException("Short circuit");
                    }

                    @Override
                    public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int maxAttempts() {
                        return 1;
                    }

                    @Override
                    public Builder toBuilder() {
                        throw new UnsupportedOperationException();
                    }
                })
                .build();

        var e = Assertions.assertThrows(TokenAcquisitionFailedException.class, () -> client.call("GetSprocket"));

        assertThat(e.getMessage(), equalTo("Short circuit"));
    }

    @Test
    public void canRetryRequests() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var calls = new ArrayList<>();
        var responseErrorCodes = new ArrayList<String>();

        var mockQueue = new MockQueue()
                .enqueue(
                        HttpResponse.create()
                                .setStatusCode(429)
                                .setBody(DataStream.ofString("{\"__type\":\"ExpiredToken\"}"))
                                .toUnmodifiable())
                .enqueue(
                        HttpResponse.create()
                                .setStatusCode(200)
                                .setBody(DataStream.ofString("{\"id\":\"1\"}"))
                                .toUnmodifiable());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .addPlugin(mock)
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readAfterAttempt(
                            OutputHook<?, ?, ?, ?> hook,
                            RuntimeException error
                    ) {
                        responseErrorCodes.add(hook.context().get(CallContext.RESPONSE_ERROR_CODE));
                    }
                })
                .endpointResolver(EndpointResolver.staticEndpoint("https://localhost:8081"))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .retryStrategy(new RetryStrategy() {
                    @Override
                    public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
                        calls.add("Acquire");
                        return new AcquireInitialTokenResponse(new Token(0), Duration.ZERO);
                    }

                    @Override
                    public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
                        calls.add("Refresh");
                        if (request.token() instanceof Token t) {
                            return new RefreshRetryTokenResponse(new Token(t.retry + 1), Duration.ZERO);
                        }
                        throw new IllegalArgumentException();
                    }

                    @Override
                    public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
                        if (request.token() instanceof Token t) {
                            calls.add("Success: " + t.retry);
                            return new RecordSuccessResponse(request.token());
                        } else {
                            throw new IllegalArgumentException();
                        }
                    }

                    @Override
                    public int maxAttempts() {
                        return 3;
                    }

                    @Override
                    public Builder toBuilder() {
                        throw new UnsupportedOperationException();
                    }
                })
                .build();

        var response = client.call("GetSprocket", Document.ofObject(Map.of("id", "1")));

        assertThat(mockQueue.remaining(), is(0));
        assertThat(response.getMember("id").asString(), equalTo("1"));
        assertThat(response, instanceOf(Document.class));
        assertThat(calls, contains("Acquire", "Refresh", "Success: 1"));
        assertThat(responseErrorCodes.size(), equalTo(2));
        assertThat(responseErrorCodes.get(0), equalTo("ExpiredToken"));
        Assertions.assertNull(responseErrorCodes.get(1));
    }

    @Test
    public void skipsAuthSchemePropertyResolutionWhenIdentityResolverIsUnavailable() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var unavailableSchemeId = ShapeId.from("smithy.test#unavailableAuth");
        var fallbackSchemeId = ShapeId.from("smithy.test#fallbackAuth");
        var fallbackSigned = new AtomicReference<Boolean>(false);

        var fallbackScheme = AuthScheme.of(
                fallbackSchemeId,
                HttpRequest.class,
                Identity.class,
                (request, identity, properties) -> {
                    fallbackSigned.set(true);
                    return new SignResult<>(request);
                });

        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{\"id\":\"1\"}"))
                        .toUnmodifiable());
        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .addPlugin(MockPlugin.builder().addQueue(mockQueue).build())
                .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
                .authSchemeResolver(params -> List.of(
                        new AuthSchemeOption(unavailableSchemeId),
                        new AuthSchemeOption(fallbackSchemeId)))
                .putSupportedAuthSchemes(
                        new PropertiesFailingAuthScheme(unavailableSchemeId),
                        fallbackScheme)
                .addIdentityResolver(identityResolver(new Identity() {}))
                .build();

        client.call("GetSprocket", Document.ofObject(Map.of("id", "1")));

        assertThat(fallbackSigned.get(), is(true));
    }

    @Test
    public void authSchemeOptionOverridesAreMergedOverSchemeProperties() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var schemeId = ShapeId.from("smithy.test#propertiesAuth");
        var BASE_KEY = Context.<String>key("scheme-only");
        var SHARED_KEY = Context.<String>key("scheme-and-option");
        var IDENTITY_KEY = Context.<String>key("identity-option-only");
        var SIGNER_KEY = Context.<String>key("signer-option-only");

        var capturedIdentityProperties = new AtomicReference<Context>();
        var capturedSignerProperties = new AtomicReference<Context>();
        var scheme = new PropertiesAuthScheme(schemeId, BASE_KEY, SHARED_KEY, capturedSignerProperties);

        var identityOverrides = Context.create()
                .put(SHARED_KEY, "identity-override")
                .put(IDENTITY_KEY, "identity-only");
        var signerOverrides = Context.create()
                .put(SHARED_KEY, "signer-override")
                .put(SIGNER_KEY, "signer-only");

        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{\"id\":\"1\"}"))
                        .toUnmodifiable());
        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .addPlugin(MockPlugin.builder().addQueue(mockQueue).build())
                .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
                .authSchemeResolver(
                        params -> List.of(new AuthSchemeOption(schemeId, identityOverrides, signerOverrides)))
                .putSupportedAuthSchemes(scheme)
                .addIdentityResolver(capturingIdentityResolver(capturedIdentityProperties))
                .build();

        client.call("GetSprocket", Document.ofObject(Map.of("id", "1")));

        var identityProperties = capturedIdentityProperties.get();
        assertThat(identityProperties.get(BASE_KEY), equalTo("scheme"));
        assertThat(identityProperties.get(SHARED_KEY), equalTo("identity-override"));
        assertThat(identityProperties.get(IDENTITY_KEY), equalTo("identity-only"));
        Assertions.assertNull(identityProperties.get(SIGNER_KEY));

        var signerProperties = capturedSignerProperties.get();
        assertThat(signerProperties.get(BASE_KEY), equalTo("scheme"));
        assertThat(signerProperties.get(SHARED_KEY), equalTo("signer-override"));
        assertThat(signerProperties.get(SIGNER_KEY), equalTo("signer-only"));
        Assertions.assertNull(signerProperties.get(IDENTITY_KEY));
    }

    @Test
    public void authSchemeOptionWithoutOverridesPassesSchemePropertiesThrough() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var schemeId = ShapeId.from("smithy.test#propertiesAuth");
        var BASE_KEY = Context.<String>key("scheme-only");
        var SHARED_KEY = Context.<String>key("scheme-and-option");

        var capturedIdentityProperties = new AtomicReference<Context>();
        var capturedSignerProperties = new AtomicReference<Context>();
        var scheme = new PropertiesAuthScheme(schemeId, BASE_KEY, SHARED_KEY, capturedSignerProperties);

        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{\"id\":\"1\"}"))
                        .toUnmodifiable());
        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .addPlugin(MockPlugin.builder().addQueue(mockQueue).build())
                .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
                .authSchemeResolver(params -> List.of(new AuthSchemeOption(schemeId)))
                .putSupportedAuthSchemes(scheme)
                .addIdentityResolver(capturingIdentityResolver(capturedIdentityProperties))
                .build();

        client.call("GetSprocket", Document.ofObject(Map.of("id", "1")));

        Assertions.assertSame(scheme.lastIdentityProperties, capturedIdentityProperties.get());
        Assertions.assertSame(scheme.lastSignerProperties, capturedSignerProperties.get());
        assertThat(capturedIdentityProperties.get().get(BASE_KEY), equalTo("scheme"));
        assertThat(capturedIdentityProperties.get().get(SHARED_KEY), equalTo("scheme"));
        assertThat(capturedSignerProperties.get().get(BASE_KEY), equalTo("scheme"));
        assertThat(capturedSignerProperties.get().get(SHARED_KEY), equalTo("scheme"));
    }

    @Test
    public void laterAuthSchemeRegistrationReplacesEarlierRegistration() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var schemeId = ShapeId.from("smithy.test#replaceableAuth");
        var first = AuthScheme.of(
                schemeId,
                HttpRequest.class,
                Identity.class,
                (request, identity, properties) -> new SignResult<>(request));
        var replacement = AuthScheme.of(
                schemeId,
                HttpRequest.class,
                Identity.class,
                (request, identity, properties) -> new SignResult<>(request));

        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .addPlugin(MockPlugin.builder().addQueue(new MockQueue()).build())
                .endpointResolver(EndpointResolver.staticEndpoint("https://example.com"))
                .putSupportedAuthSchemes(first)
                .putSupportedAuthSchemes(replacement)
                .build();

        Assertions.assertSame(replacement, client.config().supportedAuthSchemesById().get(schemeId));
        assertThat(
                client.config()
                        .supportedAuthSchemes()
                        .stream()
                        .filter(scheme -> scheme.schemeId().equals(schemeId))
                        .count(),
                equalTo(1L));
    }

    @Test
    public void endpointAuthSchemeOverridesAugmentSignerProperties() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var testSchemeId = ShapeId.from("smithy.test#testAuth");
        var TEST_KEY = Context.<String>key("test-signing-override");
        var capturedProperties = new AtomicReference<Context>();
        var capturedIdentity = new AtomicReference<Identity>();
        var capturedResolver = new AtomicReference<IdentityResolver<?>>();
        var defaultIdentity = new Identity() {};
        var overrideIdentity = new Identity() {};
        var defaultResolver = identityResolver(defaultIdentity);
        var overrideResolver = identityResolver(overrideIdentity);

        // Auth scheme with a signer that captures the properties it receives.
        var testScheme = AuthScheme.of(
                testSchemeId,
                HttpRequest.class,
                Identity.class,
                (request, identity, properties) -> {
                    capturedProperties.set(properties);
                    return new SignResult<>(request);
                });

        // Endpoint resolver that returns an endpoint with an auth scheme override.
        EndpointResolver endpointResolver = params -> Endpoint.builder()
                .uri("https://example.com")
                .addAuthScheme(
                        EndpointAuthScheme.builder()
                                .authSchemeId(testSchemeId.toString())
                                .putProperty(TEST_KEY, "overridden-value")
                                .build())
                .build();

        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{\"id\":\"1\"}"))
                        .toUnmodifiable());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(endpointResolver)
                .authSchemeResolver(params -> List.of(new AuthSchemeOption(testSchemeId)))
                .putSupportedAuthSchemes(testScheme)
                .addIdentityResolver(defaultResolver)
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
                        capturedIdentity.set(hook.context().get(CallContext.IDENTITY));
                        capturedResolver.set(hook.context().get(CallContext.IDENTITY_RESOLVER));
                    }
                })
                .build();

        client.call(
                "GetSprocket",
                Document.ofObject(Map.of("id", "1")),
                RequestOverrideConfig.builder().addIdentityResolver(overrideResolver).build());

        assertThat(capturedProperties.get().get(TEST_KEY), equalTo("overridden-value"));
        assertThat(capturedIdentity.get(), is(overrideIdentity));
        assertThat(capturedResolver.get(), is(overrideResolver));
    }

    @Test
    public void endpointAuthSchemeSwapsToDifferentScheme() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var resolverChosenId = ShapeId.from("smithy.test#chosenAuth");
        var endpointTargetId = ShapeId.from("smithy.test#targetAuth");
        var TEST_KEY = Context.<String>key("test-signing-override");

        var resolverScheme = AuthScheme.of(
                resolverChosenId,
                HttpRequest.class,
                Identity.class,
                (request, identity, properties) -> {
                    throw new AssertionError(
                            "Resolver-chosen scheme should not have signed: endpoint asked for "
                                    + endpointTargetId);
                });

        var capturedProperties = new AtomicReference<Context>();
        var capturedIdentity = new AtomicReference<Identity>();
        var capturedResolver = new AtomicReference<IdentityResolver<?>>();
        var targetIdentity = TokenIdentity.create("target");
        IdentityResolver<TokenIdentity> targetResolver = new IdentityResolver<>() {
            @Override
            public IdentityResult<TokenIdentity> resolveIdentity(Context requestProperties) {
                return IdentityResult.of(targetIdentity);
            }

            @Override
            public Class<TokenIdentity> identityType() {
                return TokenIdentity.class;
            }
        };
        var targetScheme = AuthScheme.of(
                endpointTargetId,
                HttpRequest.class,
                TokenIdentity.class,
                (request, identity, properties) -> {
                    capturedProperties.set(properties);
                    return new SignResult<>(request);
                });

        EndpointResolver endpointResolver = params -> Endpoint.builder()
                .uri("https://example.com")
                .addAuthScheme(
                        EndpointAuthScheme.builder()
                                .authSchemeId(endpointTargetId.toString())
                                .putProperty(TEST_KEY, "from-endpoint")
                                .build())
                .build();

        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{\"id\":\"1\"}"))
                        .toUnmodifiable());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(endpointResolver)
                .authSchemeResolver(params -> List.of(new AuthSchemeOption(resolverChosenId)))
                .putSupportedAuthSchemes(resolverScheme, targetScheme)
                .addIdentityResolver(new IdentityResolver<>() {
                    @Override
                    public IdentityResult<Identity> resolveIdentity(Context requestProperties) {
                        return IdentityResult.of(new Identity() {});
                    }

                    @Override
                    public Class<Identity> identityType() {
                        return Identity.class;
                    }
                }, targetResolver)
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
                        capturedIdentity.set(hook.context().get(CallContext.IDENTITY));
                        capturedResolver.set(hook.context().get(CallContext.IDENTITY_RESOLVER));
                    }
                })
                .build();

        client.call("GetSprocket", Document.ofObject(Map.of("id", "1")));

        assertThat(capturedProperties.get().get(TEST_KEY), equalTo("from-endpoint"));
        assertThat(capturedIdentity.get(), is(targetIdentity));
        assertThat(capturedResolver.get(), is(targetResolver));
    }

    @Test
    public void endpointAuthSchemeSwapSkipsIncompatibleRequestTypes() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var resolverChosenId = ShapeId.from("smithy.test#chosenAuth");
        var incompatibleId = ShapeId.from("smithy.test#incompatibleAuth");
        var compatibleId = ShapeId.from("smithy.test#compatibleAuth");
        var compatibleSigned = new AtomicReference<Boolean>(false);

        var resolverScheme = AuthScheme.of(
                resolverChosenId,
                HttpRequest.class,
                Identity.class,
                (request, identity, properties) -> {
                    throw new AssertionError("Endpoint auth scheme should replace the resolver-chosen scheme");
                });
        var incompatibleScheme = AuthScheme.of(
                incompatibleId,
                String.class,
                Identity.class,
                (request, identity, properties) -> {
                    throw new AssertionError("Incompatible auth scheme should not be selected");
                });
        var compatibleScheme = AuthScheme.of(
                compatibleId,
                HttpRequest.class,
                Identity.class,
                (request, identity, properties) -> {
                    compatibleSigned.set(true);
                    return new SignResult<>(request);
                });

        EndpointResolver endpointResolver = params -> Endpoint.builder()
                .uri("https://example.com")
                .addAuthScheme(EndpointAuthScheme.builder()
                        .authSchemeId(incompatibleId.toString())
                        .build())
                .addAuthScheme(EndpointAuthScheme.builder()
                        .authSchemeId(compatibleId.toString())
                        .build())
                .build();

        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{\"id\":\"1\"}"))
                        .toUnmodifiable());
        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .addPlugin(MockPlugin.builder().addQueue(mockQueue).build())
                .endpointResolver(endpointResolver)
                .authSchemeResolver(params -> List.of(new AuthSchemeOption(resolverChosenId)))
                .putSupportedAuthSchemes(resolverScheme, incompatibleScheme, compatibleScheme)
                .addIdentityResolver(identityResolver(new Identity() {}))
                .build();

        client.call("GetSprocket", Document.ofObject(Map.of("id", "1")));

        assertThat(compatibleSigned.get(), is(true));
    }

    @Test
    public void endpointAuthSchemeUnsupportedNameFails() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var resolverChosenId = ShapeId.from("smithy.test#chosenAuth");
        var endpointTargetId = ShapeId.from("smithy.test#unknownAuth");

        var resolverScheme = AuthScheme.of(
                resolverChosenId,
                HttpRequest.class,
                Identity.class,
                (request, identity, properties) -> new SignResult<>(request));

        EndpointResolver endpointResolver = params -> Endpoint.builder()
                .uri("https://example.com")
                .addAuthScheme(
                        EndpointAuthScheme.builder()
                                .authSchemeId(endpointTargetId.toString())
                                .build())
                .build();

        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{\"id\":\"1\"}"))
                        .toUnmodifiable());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .addPlugin(mock)
                .endpointResolver(endpointResolver)
                .authSchemeResolver(params -> List.of(new AuthSchemeOption(resolverChosenId)))
                .putSupportedAuthSchemes(resolverScheme)
                .addIdentityResolver(new IdentityResolver<>() {
                    @Override
                    public IdentityResult<Identity> resolveIdentity(Context requestProperties) {
                        return IdentityResult.of(new Identity() {});
                    }

                    @Override
                    public Class<Identity> identityType() {
                        return Identity.class;
                    }
                })
                .build();

        var ex = Assertions.assertThrows(
                Exception.class,
                () -> client.call("GetSprocket", Document.ofObject(Map.of("id", "1"))));
        assertThat(ex.getMessage(), Matchers.containsString(endpointTargetId.toString()));
    }

    private static IdentityResolver<Identity> identityResolver(Identity identity) {
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<Identity> resolveIdentity(Context requestProperties) {
                return IdentityResult.of(identity);
            }

            @Override
            public Class<Identity> identityType() {
                return Identity.class;
            }
        };
    }

    private static IdentityResolver<Identity> capturingIdentityResolver(AtomicReference<Context> captured) {
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<Identity> resolveIdentity(Context requestProperties) {
                captured.set(requestProperties);
                return IdentityResult.of(new Identity() {});
            }

            @Override
            public Class<Identity> identityType() {
                return Identity.class;
            }
        };
    }

    @Test
    public void releasesThePooledBodyAfterRetriesEvenWhenAnInterceptorReplacesItInPlace() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var created = new ArrayList<HttpRequest>();
        var pooledBodies = new ArrayList<PooledDataStream>();
        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(429)
                        .setBody(DataStream.ofString("{\"__type\":\"ExpiredToken\"}"))
                        .toUnmodifiable())
                .enqueue(HttpResponse.create()
                        .setStatusCode(200)
                        .setBody(DataStream.ofString("{\"id\":\"1\"}"))
                        .toUnmodifiable());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .protocol(new CapturingProtocol(new RestJsonClientProtocol(service), created, pooledBodies))
                .addPlugin(mock)
                .addInterceptor(new ClientInterceptor() {
                    @Override
                    public <RequestT> RequestT modifyBeforeRetryLoop(RequestHook<?, ?, RequestT> hook) {
                        var req = (HttpRequest) hook.request();
                        return hook.asRequestType(
                                req.toModifiable().setBody(DataStream.ofString("{\"id\":\"swapped\"}")));
                    }
                })
                .endpointResolver(EndpointResolver.staticEndpoint("https://localhost:8081"))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .retryStrategy(retryingStrategy(3))
                .build();

        var response = client.call("GetSprocket", Document.ofObject(Map.of("id", "1")));

        assertThat(response.getMember("id").asString(), equalTo("1"));
        assertThat(mockQueue.remaining(), is(0));
        assertThat(mock.getRequests(), Matchers.hasSize(2));

        assertThat(created, Matchers.hasSize(1));
        assertThat(pooledBodies, Matchers.hasSize(1));
        assertThat(created.get(0).body(), Matchers.not(instanceOf(PooledDataStream.class)));

        var pooled = pooledBodies.get(0);
        assertThat(pooled.isReleased(), is(true));
        assertThat(pooled.escaped(), is(false));
        Assertions.assertThrows(IllegalStateException.class, pooled::asByteBuffer);
    }

    @Test
    public void releasesThePooledBodyWhenTheCallFails() {
        var service = ShapeId.from("smithy.example#Sprockets");
        var created = new ArrayList<HttpRequest>();
        var pooledBodies = new ArrayList<PooledDataStream>();
        var mockQueue = new MockQueue()
                .enqueue(HttpResponse.create()
                        .setStatusCode(500)
                        .setBody(DataStream.ofString("{\"__type\":\"ServiceFooError\"}"))
                        .toUnmodifiable());
        var mock = MockPlugin.builder().addQueue(mockQueue).build();

        var client = DynamicClient.builder()
                .serviceId(service)
                .model(MODEL)
                .protocol(new CapturingProtocol(new RestJsonClientProtocol(service), created, pooledBodies))
                .addPlugin(mock)
                .endpointResolver(EndpointResolver.staticEndpoint("https://localhost:8081"))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .retryStrategy(retryingStrategy(1))
                .build();

        Assertions.assertThrows(CallException.class,
                () -> client.call("GetSprocket", Document.ofObject(Map.of("id", "1"))));

        assertThat(pooledBodies, Matchers.hasSize(1));
        assertThat(pooledBodies.get(0).isReleased(), is(true));
    }

    private static RetryStrategy retryingStrategy(int maxAttempts) {
        return new RetryStrategy() {
            @Override
            public AcquireInitialTokenResponse acquireInitialToken(AcquireInitialTokenRequest request) {
                return new AcquireInitialTokenResponse(new Token(0), Duration.ZERO);
            }

            @Override
            public RefreshRetryTokenResponse refreshRetryToken(RefreshRetryTokenRequest request) {
                if (request.token() instanceof Token t && t.retry + 1 < maxAttempts) {
                    return new RefreshRetryTokenResponse(new Token(t.retry + 1), Duration.ZERO);
                }
                throw new TokenAcquisitionFailedException("No more retries");
            }

            @Override
            public RecordSuccessResponse recordSuccess(RecordSuccessRequest request) {
                return new RecordSuccessResponse(request.token());
            }

            @Override
            public int maxAttempts() {
                return maxAttempts;
            }

            @Override
            public Builder toBuilder() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class PropertiesAuthScheme implements AuthScheme<HttpRequest, Identity> {
        private final ShapeId schemeId;
        private final Context.Key<String> baseKey;
        private final Context.Key<String> sharedKey;
        private final AtomicReference<Context> capturedSignerProperties;
        volatile Context lastIdentityProperties;
        volatile Context lastSignerProperties;

        PropertiesAuthScheme(
                ShapeId schemeId,
                Context.Key<String> baseKey,
                Context.Key<String> sharedKey,
                AtomicReference<Context> capturedSignerProperties
        ) {
            this.schemeId = schemeId;
            this.baseKey = baseKey;
            this.sharedKey = sharedKey;
            this.capturedSignerProperties = capturedSignerProperties;
        }

        @Override
        public ShapeId schemeId() {
            return schemeId;
        }

        @Override
        public Class<HttpRequest> requestClass() {
            return HttpRequest.class;
        }

        @Override
        public Class<Identity> identityClass() {
            return Identity.class;
        }

        @Override
        public Context getIdentityProperties(Context context) {
            lastIdentityProperties = Context.unmodifiableView(
                    Context.create().put(baseKey, "scheme").put(sharedKey, "scheme"));
            return lastIdentityProperties;
        }

        @Override
        public Context getSignerProperties(Context context) {
            lastSignerProperties = Context.unmodifiableView(
                    Context.create().put(baseKey, "scheme").put(sharedKey, "scheme"));
            return lastSignerProperties;
        }

        @Override
        public Signer<HttpRequest, Identity> signer() {
            return (request, identity, properties) -> {
                capturedSignerProperties.set(properties);
                return new SignResult<>(request);
            };
        }
    }

    private static final class CapturingProtocol implements ClientProtocol<HttpRequest, HttpResponse> {
        private final ClientProtocol<HttpRequest, HttpResponse> delegate;
        private final List<HttpRequest> created;
        private final List<PooledDataStream> pooledBodies;

        CapturingProtocol(
                ClientProtocol<HttpRequest, HttpResponse> delegate,
                List<HttpRequest> created,
                List<PooledDataStream> pooledBodies
        ) {
            this.delegate = delegate;
            this.created = created;
            this.pooledBodies = pooledBodies;
        }

        @Override
        public ShapeId id() {
            return delegate.id();
        }

        @Override
        public Codec payloadCodec() {
            return delegate.payloadCodec();
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return delegate.messageExchange();
        }

        @Override
        public <I extends SerializableStruct, O extends SerializableStruct> HttpRequest createRequest(
                ApiOperation<I, O> operation,
                I input,
                Context context,
                SmithyUri endpoint
        ) {
            var request = delegate.createRequest(operation, input, context, endpoint);
            created.add(request);
            return request;
        }

        @Override
        public PooledDataStream pooledRequestBody(HttpRequest request) {
            var pooled = delegate.pooledRequestBody(request);
            if (pooled != null) {
                pooledBodies.add(pooled);
            }
            return pooled;
        }

        @Override
        public HttpRequest setServiceEndpoint(HttpRequest request, Endpoint endpoint) {
            return delegate.setServiceEndpoint(request, endpoint);
        }

        @Override
        public <I extends SerializableStruct, O extends SerializableStruct> O deserializeResponse(
                ApiOperation<I, O> operation,
                Context context,
                TypeRegistry errorRegistry,
                HttpRequest request,
                HttpResponse response
        ) {
            return delegate.deserializeResponse(operation, context, errorRegistry, request, response);
        }
    }

    private static final class PropertiesFailingAuthScheme implements AuthScheme<HttpRequest, TokenIdentity> {
        private final ShapeId schemeId;

        private PropertiesFailingAuthScheme(ShapeId schemeId) {
            this.schemeId = schemeId;
        }

        @Override
        public ShapeId schemeId() {
            return schemeId;
        }

        @Override
        public Class<HttpRequest> requestClass() {
            return HttpRequest.class;
        }

        @Override
        public Class<TokenIdentity> identityClass() {
            return TokenIdentity.class;
        }

        @Override
        public Context getSignerProperties(Context context) {
            throw new AssertionError("Signer properties must not be resolved without an identity resolver");
        }

        @Override
        public Context getIdentityProperties(Context context) {
            throw new AssertionError("Identity properties must not be resolved without an identity resolver");
        }

        @Override
        public Signer<HttpRequest, TokenIdentity> signer() {
            throw new AssertionError("Unavailable auth scheme must not be used");
        }
    }

    private static final class Token implements RetryToken {
        int retry;

        Token(int retry) {
            this.retry = retry;
        }
    }
}
