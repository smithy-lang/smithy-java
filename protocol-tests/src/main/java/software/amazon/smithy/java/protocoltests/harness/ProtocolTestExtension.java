/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaSymbolProvider;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.client.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.auth.api.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.runtime.client.core.ProtocolSettings;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.protocoltests.traits.HttpMalformedRequestTestCase;
import software.amazon.smithy.protocoltests.traits.HttpMalformedRequestTestsTrait;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Junit5 extension that determines common information and configuration for protocol test execution.
 *
 * <p>See the {@link ProtocolTest} annotation for usage information.
 */
@SmithyInternalApi
public final class ProtocolTestExtension implements BeforeAllCallback {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(ProtocolTestExtension.class);
    private static final Model BASE_MODEL = Model.assembler(ProtocolTestExtension.class.getClassLoader())
        .discoverModels(ProtocolTestExtension.class.getClassLoader())
        .assemble()
        .unwrap();
    private static final ModelTransformer transformer = ModelTransformer.create();
    private static final ExtensionContext.Namespace namespace = ExtensionContext.Namespace.create(
        ProtocolTestExtension.class
    );
    private static final Map<ShapeId, ClientProtocolFactory> protocolFactories = new HashMap<>();
    private static final Map<ShapeId, AuthSchemeFactory> authSchemeFactories = new HashMap<>();
    static {
        ServiceLoader.load(ClientProtocolFactory.class)
            .forEach(factory -> protocolFactories.put(factory.id(), factory));
        ServiceLoader.load(AuthSchemeFactory.class)
            .forEach(factory -> authSchemeFactories.put(factory.schemeId(), factory));
    }
    private static final String SHARED_DATA_KEY = "protocol-test-shared-data";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var testClass = context.getRequiredTestClass();
        var protocolTestAnnotation = testClass.getAnnotation(ProtocolTest.class);
        if (protocolTestAnnotation == null) {
            throw new IllegalArgumentException("`@ProtocolTest` annotation not found.");
        }
        var serviceId = ShapeId.from(protocolTestAnnotation.service());

        var filter = TestFilter.fromAnnotation(testClass.getAnnotation(ProtocolTestFilter.class));
        Model filtered = getFilteredModel(filter);

        // Apply basic service transforms
        ServiceShape service = filtered.expectShape(serviceId).asServiceShape().orElseThrow();
        Model serviceModel = applyServiceTransformations(service, filtered);

        // Discover all protocols and operations applicable for the service under test.
        var protocols = getProtocols(serviceModel, service);
        var testOperations = getTestOperations(serviceModel, service, filter);

        // instantiate a mock client for use by test runners
        var mockClientBuilder = MockClient.builder();

        // Discover all client auth scheme implementations that could be used for tests
        // NOTE: auth schemes are not stored in test data b/c they are added to the mock client
        var authSchemes = getAuthSchemes(serviceModel, service);
        for (var authScheme : authSchemes.values()) {
            mockClientBuilder.putSupportedAuthSchemes(authScheme);
        }

        // TODO: allow customization of client builder?

        // Store shared data for use by tests
        context.getStore(namespace.append(context.getUniqueId()))
            .put(
                SHARED_DATA_KEY,
                new SharedTestData(
                    mockClientBuilder.build(),
                    protocols,
                    testOperations
                )
            );
    }

    static SharedTestData getSharedTestData(ExtensionContext context) {
        return context.getStore(namespace.append(context.getUniqueId())).get(SHARED_DATA_KEY, SharedTestData.class);
    }

    /**
     * Wrapper class for protocol test data. Allows the retrieval of data from the extension store without casting.
     * @param mockClient
     */
    record SharedTestData(
        MockClient mockClient,
        Map<ShapeId, ClientProtocol<?, ?>> protocols,
        List<HttpTestOperation> operations
    ) {
        ClientProtocol<?, ?> getProtocol(ShapeId shapeId) {
            return protocols.get(shapeId);
        }
    }

    private static Model getFilteredModel(TestFilter filter) {
        return transformer.removeUnreferencedShapes(
            transformer.removeShapesIf(BASE_MODEL, s -> s.isOperationShape() && filter.skipOperation(s.getId()))
        );
    }

    private static Model applyServiceTransformations(ServiceShape service, Model filtered) {
        Model serviceModel = transformer.copyServiceErrorsToOperations(filtered, service);
        serviceModel = transformer.flattenAndRemoveMixins(serviceModel);
        serviceModel = transformer.createDedicatedInputAndOutput(serviceModel, "Input", "Output");
        return serviceModel;
    }

    @SuppressWarnings("unchecked")
    static Map<ShapeId, ClientProtocol<?, ?>> getProtocols(Model serviceModel, ServiceShape service) {
        var serviceIndex = ServiceIndex.of(serviceModel);

        var protocolTraits = serviceIndex.getProtocols(service.toShapeId());
        if (protocolTraits.isEmpty()) {
            LOGGER.error("Service {} has no protocol traits.", service.getId());
            throw new UnsupportedOperationException("No protocol traits found on " + service.getId());
        }

        var protocols = new HashMap<ShapeId, ClientProtocol<?, ?>>();
        for (var protocolTraitEntry : protocolTraits.entrySet()) {
            var protocolFactory = protocolFactories.get(protocolTraitEntry.getKey());
            if (protocolFactory == null) {
                continue;
            }
            var protocolSettings = ProtocolSettings.builder().namespace(service.getId()).build();
            var instance = protocolFactory.createProtocol(protocolSettings, protocolTraitEntry.getValue());
            protocols.put(protocolTraitEntry.getKey(), instance);
        }

        if (protocols.isEmpty()) {
            LOGGER.error(
                "Service {} has no protocol implementations. Expected one of: {}",
                service.getId(),
                protocolTraits.keySet()
            );
            throw new UnsupportedOperationException("No protocol implementations found for " + service.getId());
        }

        return protocols;
    }

    @SuppressWarnings("unchecked")
    private static Map<ShapeId, AuthScheme<?, ?>> getAuthSchemes(Model serviceModel, ServiceShape service) {
        var serviceIndex = ServiceIndex.of(serviceModel);

        var authSchemes = serviceIndex.getAuthSchemes(service.toShapeId());
        if (authSchemes.isEmpty()) {
            LOGGER.debug("No auth schemes found on {}", service.getId());
            return Collections.emptyMap();
        }
        Map<ShapeId, AuthScheme<?, ?>> result = new HashMap<>();
        for (var schemeEntry : authSchemes.entrySet()) {
            var factory = authSchemeFactories.get(schemeEntry.getKey());
            if (factory == null) {
                LOGGER.debug("No auth scheme factories found for authScheme {}", schemeEntry.getKey());
                continue;
            }
            result.put(schemeEntry.getKey(), factory.createAuthScheme(schemeEntry.getValue()));
        }

        return result;
    }

    private static List<HttpTestOperation> getTestOperations(
        Model serviceModel,
        ServiceShape service,
        TestFilter filter
    ) {
        List<HttpTestOperation> result = new ArrayList<>();

        var symbolProvider = new JavaSymbolProvider(serviceModel, service, service.toShapeId().getNamespace());
        for (var operationId : service.getOperations()) {
            var operationShape = serviceModel.getShape(operationId);
            if (operationShape.isPresent()) {
                var operation = operationShape.get();
                var apiOperation = getApiOperation(symbolProvider, operation);
                List<HttpRequestTestCase> requestTestsCases = new ArrayList<>();
                List<HttpResponseTestCase> responseTestsCases = new ArrayList<>();
                List<HttpMalformedRequestTestCase> malformedRequestTestCases = new ArrayList<>();
                operation.getTrait(HttpRequestTestsTrait.class)
                    .map(HttpRequestTestsTrait::getTestCases)
                    .map(l -> l.stream().filter(tc -> !filter.skipTestCase(tc)).toList())
                    .ifPresent(requestTestsCases::addAll);
                operation.getTrait(HttpResponseTestsTrait.class)
                    .map(HttpResponseTestsTrait::getTestCases)
                    .map(l -> l.stream().filter(tc -> !filter.skipTestCase(tc)).toList())
                    .ifPresent(responseTestsCases::addAll);
                operation.getTrait(HttpMalformedRequestTestsTrait.class)
                    .map(HttpMalformedRequestTestsTrait::getTestCases)
                    .ifPresent(malformedRequestTestCases::addAll);
                result.add(
                    new HttpTestOperation(
                        operationId.toShapeId(),
                        apiOperation,
                        requestTestsCases,
                        responseTestsCases,
                        malformedRequestTestCases
                    )
                );
            }
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No test operations found for " + service.getId());
        }

        return result;
    }

    private static ApiOperation<?, ?> getApiOperation(SymbolProvider provider, Shape shape) {
        try {
            var fqn = provider.toSymbol(shape).getFullName();
            return CodegenUtils.getImplementationByName(ApiOperation.class, fqn).getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException
            | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
