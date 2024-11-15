/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.integrations.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.ModifiableHttpHeaders;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.server.Route;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.Handler;
import software.amazon.smithy.java.server.core.HandlerAssembler;
import software.amazon.smithy.java.server.core.HttpJob;
import software.amazon.smithy.java.server.core.HttpRequest;
import software.amazon.smithy.java.server.core.HttpResponse;
import software.amazon.smithy.java.server.core.Orchestrator;
import software.amazon.smithy.java.server.core.ProtocolResolver;
import software.amazon.smithy.java.server.core.ServiceMatcher;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionResult;
import software.amazon.smithy.java.server.core.SingleThreadOrchestrator;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Represents a Smithy-based AWS Lambda request handler that is constructed using a service object.
 * <br>
 * The endpoint is responsible for processing a request object into the form which the service expects, and then
 * processing the output from the service into a response object.
 * <br>
 * It should not be constructed directly, except by the Lambda runtime and/or for testing purposes.
 */
@SmithyUnstableApi
public final class LambdaEndpoint implements RequestHandler<ProxyRequest, ProxyResponse> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(LambdaEndpoint.class);
    private static final ServiceLoader<SmithyServiceProvider> LOADER;
    private static final List<Service> SERVICES;
    private static final Orchestrator ORCHESTRATOR;
    private static final ProtocolResolver RESOLVER;

    static {
        LOADER = ServiceLoader.load(SmithyServiceProvider.class);
        SERVICES = LOADER.stream().map(s -> s.get().get()).toList();
        if (SERVICES.isEmpty()) {
            throw new IllegalStateException("At least one service must be provided.");
        }
        // TODO: Actual multi-service handling (?)
        ORCHESTRATOR = buildOrchestrator(SERVICES);
        RESOLVER = buildProtocolResolver(SERVICES);
        // TODO: Add some kind of configuration object
    }

    public LambdaEndpoint() {}

    @Override
    public ProxyResponse handleRequest(ProxyRequest proxyRequest, Context context) {
        // TODO: Improve error handling
        HttpRequest request = getRequest(proxyRequest);
        HttpJob job = getJob(request, RESOLVER);
        try {
            ORCHESTRATOR.enqueue(job).get();
        } catch (InterruptedException | ExecutionException e) {
            // TODO: Handle modeled errors (pending error serialization?)
            LOGGER.error("Job failed: ", e);
        }
        ProxyResponse response = getResponse(job.response(), proxyRequest.getIsBase64Encoded());
        return response;
    }

    private static Orchestrator buildOrchestrator(List<Service> services) {
        List<Handler> handlers = new HandlerAssembler().assembleHandlers(services);
        return new SingleThreadOrchestrator(handlers);
    }

    private static ProtocolResolver buildProtocolResolver(List<Service> services) {
        Route route = Route.builder().pathPrefix("/").services(services).build();
        return new ProtocolResolver(new ServiceMatcher(List.of(route)));
    }

    private static HttpRequest getRequest(ProxyRequest proxyRequest) {
        String method = proxyRequest.getHttpMethod();
        String encodedUri = URLEncoder.encode(proxyRequest.getPath(), StandardCharsets.UTF_8);
        ModifiableHttpHeaders headers = HttpHeaders.ofModifiable();
        if (proxyRequest.getMultiValueHeaders() != null && !proxyRequest.getMultiValueHeaders().isEmpty()) {
            // TODO: handle single-value headers?
            // -- APIGW puts the actual headers in both, but only the latest header per key
            headers.putHeaders(proxyRequest.getMultiValueHeaders());
        }
        URI uri;
        if (proxyRequest.getMultiValueQueryStringParameters() != null && !proxyRequest
            .getMultiValueQueryStringParameters()
            .isEmpty()) {
            // TODO: handle single-value params?
            // -- APIGW puts the actual params in both, but only the latest param per key
            Map<String, List<String>> params = proxyRequest.getMultiValueQueryStringParameters();
            StringBuilder encodedParams = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : params.entrySet()) {
                for (String value : entry.getValue()) {
                    encodedParams.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                    encodedParams.append('=');
                    encodedParams.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                    encodedParams.append('&');
                }
            }
            encodedParams.setLength(encodedParams.length() - 1);
            uri = URI.create(encodedUri + "?" + encodedParams);
        } else {
            // TODO: handle stage?
            uri = URI.create(encodedUri);
        }
        HttpRequest request = new HttpRequest(headers, uri, method);
        if (proxyRequest.getBody() != null) {
            // TODO: handle content-type intelligently?
            String contentType = headers.firstValue("content-type");
            // TODO: handle base64 encoding
            byte[] bytes;
            if (proxyRequest.getIsBase64Encoded()) {
                bytes = Base64.getDecoder().decode(proxyRequest.getBody());
            } else {
                bytes = proxyRequest.getBody().getBytes(StandardCharsets.UTF_8);
            }
            request.setDataStream(DataStream.ofBytes(bytes, contentType));
        }
        return request;
    }

    private static HttpJob getJob(HttpRequest request, ProtocolResolver resolver) {
        ServiceProtocolResolutionResult resolutionResult = resolver.resolve(
            new ServiceProtocolResolutionRequest(request.uri(), request.headers(), request.context(), request.method())
        );
        HttpResponse response = new HttpResponse((ModifiableHttpHeaders) request.headers());
        HttpJob job = new HttpJob(resolutionResult.operation(), resolutionResult.protocol(), request, response);
        return job;
    }

    private static ProxyResponse getResponse(HttpResponse httpResponse, boolean shouldBase64Encode) {
        // TODO: Add response headers
        ProxyResponse.Builder builder = ProxyResponse.builder()
            .multiValueHeaders(httpResponse.headers().map())
            .statusCode(httpResponse.getStatusCode());

        DataStream val = httpResponse.getSerializedValue();
        if (val != null) {
            ByteBuffer buf = val.waitForByteBuffer();
            String body;
            // TODO: handle base64 encoding better
            if (shouldBase64Encode) {
                builder.isBase64Encoded(true);
                // TODO: don't use array(), or determine if we can just *not* use `String` for the body
                body = Base64.getEncoder().encodeToString(buf.array());
            } else {
                body = StandardCharsets.UTF_8.decode(buf).toString();
            }
            builder.body(body);
        }

        ProxyResponse response = builder.build();
        return response;
    }
}
