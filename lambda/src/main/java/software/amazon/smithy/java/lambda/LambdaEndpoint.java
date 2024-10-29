/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import software.amazon.smithy.java.logging.InternalLogger;
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

public final class LambdaEndpoint {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(LambdaEndpoint.class);

    private final Service service;
    private final Orchestrator orchestrator;
    private final ProtocolResolver resolver;

    // TODO: Add some kind of configuration object
    public LambdaEndpoint(Service service) {
        this.service = service;
        this.orchestrator = buildOrchestrator(service);
        this.resolver = buildProtocolResolver(service);
    }

    public ProxyResponse handleRequest(ProxyRequest proxyRequest, Context context) {
        // TODO: Improve error handling
        HttpRequest request = getRequest(proxyRequest);
        HttpJob job = getJob(request, resolver);
        try {
            orchestrator.enqueue(job).get();
        } catch (InterruptedException | ExecutionException e) {
            // TODO: Handle modeled errors (pending error serialization?)
            LOGGER.error("Job failed: ", e);
        }
        ProxyResponse response = getResponse(job.response());
        return response;
    }

    private static Orchestrator buildOrchestrator(Service service) {
        List<Handler> handlers = new HandlerAssembler().assembleHandlers(List.of(service));
        return new SingleThreadOrchestrator(handlers);
    }

    private static ProtocolResolver buildProtocolResolver(Service service) {
        Route route = Route.builder().pathPrefix("/").services(List.of(service)).build();
        return new ProtocolResolver(new ServiceMatcher(List.of(route)));
    }

    private static HttpRequest getRequest(ProxyRequest proxyRequest) {
        String method = proxyRequest.getHttpMethod();
        String encodedUri = URLEncoder.encode(proxyRequest.getPath(), StandardCharsets.UTF_8);
        ModifiableHttpHeaders headers = ModifiableHttpHeaders.create();
        if (proxyRequest.getMultiValueHeaders() != null && !proxyRequest.getMultiValueHeaders().isEmpty()) {
            // TODO: handle single-value headers?
            // -- APIGW puts the actual headers in both, but only the latest header per key
            headers.putHeader(proxyRequest.getMultiValueHeaders());
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
            request.setDataStream(DataStream.ofString(proxyRequest.getBody()));
        }
        return request;
    }

    private static HttpJob getJob(HttpRequest request, ProtocolResolver resolver) {
        ServiceProtocolResolutionResult resolutionResult = resolver.resolve(
            new ServiceProtocolResolutionRequest(request.uri(), request.headers(), request.context())
        );
        HttpResponse response = new HttpResponse((ModifiableHttpHeaders) request.headers());
        HttpJob job = new HttpJob(resolutionResult.operation(), resolutionResult.protocol(), request, response);
        return job;
    }

    private static ProxyResponse getResponse(HttpResponse httpResponse) {
        // TODO: Add response headers
        ProxyResponse.Builder builder = ProxyResponse.builder()
            .multiValueHeaders(httpResponse.headers().toMap())
            .statusCode(httpResponse.getStatusCode());

        DataStream val = httpResponse.getSerializedValue();
        if (val != null) {
            // TODO: handle base64 encoding
            ByteBuffer buf = val.waitForByteBuffer();
            String body = StandardCharsets.UTF_8.decode(buf).toString();
            builder.body(body);
        }

        ProxyResponse response = builder.build();
        return response;
    }
}
