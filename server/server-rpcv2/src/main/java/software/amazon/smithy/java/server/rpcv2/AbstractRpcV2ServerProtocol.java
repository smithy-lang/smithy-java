/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.rpcv2;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.framework.model.MalformedRequestException;
import software.amazon.smithy.java.framework.model.UnknownOperationException;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.Job;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionResult;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Shared base for RPC v2 server protocol implementations.
 *
 * <p>Subclasses provide the codec, media type, smithy-protocol header value, and
 * protocol ID that distinguish the concrete wire format (CBOR vs JSON). URL path
 * parsing, operation resolution, input deserialization, and output serialization
 * are handled here.
 */
@SmithyInternalApi
public abstract class AbstractRpcV2ServerProtocol extends ServerProtocol {

    private static final String SMITHY_PROTOCOL_PREFIX = "rpc-v2-";
    // length of "application/"
    private static final int MEDIA_TYPE_PREFIX_LENGTH = 12;

    private final String payloadMediaType;
    private final String smithyProtocolValue;
    private final boolean allowFullyQualifiedService;

    /**
     * @param services         the list of services this protocol handles
     * @param payloadMediaType the media type for request/response payloads (e.g. "application/cbor")
     */
    protected AbstractRpcV2ServerProtocol(List<Service> services, String payloadMediaType) {
        this(services, payloadMediaType, false);
    }

    /**
     * @param services                    the list of services this protocol handles
     * @param payloadMediaType            the media type for request/response payloads (e.g. "application/cbor")
     * @param allowFullyQualifiedService  whether to accept fully qualified service names in the URI
     */
    protected AbstractRpcV2ServerProtocol(
            List<Service> services,
            String payloadMediaType,
            boolean allowFullyQualifiedService
    ) {
        super(services);
        this.payloadMediaType = payloadMediaType;
        this.smithyProtocolValue = SMITHY_PROTOCOL_PREFIX
                + payloadMediaType.substring(MEDIA_TYPE_PREFIX_LENGTH);
        this.allowFullyQualifiedService = allowFullyQualifiedService;
    }

    /** Returns the codec used for serialization and deserialization. */
    protected abstract Codec codec();

    @Override
    public ServiceProtocolResolutionResult resolveOperation(
            ServiceProtocolResolutionRequest request,
            List<Service> candidates
    ) {
        if (!isRpcV2Request(request)) {
            return null;
        }
        String path = request.uri().getPath();
        var serviceAndOperation = parseRpcV2StylePath(path);
        if (!allowFullyQualifiedService && serviceAndOperation.isFullyQualifiedService()) {
            throw UnknownOperationException.builder().message("Invalid RpcV2 URI").build();
        }
        Service selectedService = null;
        if (candidates.size() == 1) {
            Service service = candidates.get(0);
            if (matchService(service, serviceAndOperation)) {
                selectedService = service;
            }
        } else {
            for (Service service : candidates) {
                if (matchService(service, serviceAndOperation)) {
                    selectedService = service;
                    break;
                }
            }
        }
        if (selectedService == null) {
            throw UnknownOperationException.builder().build();
        }
        return new ServiceProtocolResolutionResult(
                selectedService,
                selectedService.getOperation(serviceAndOperation.operation),
                this);
    }

    @Override
    public CompletableFuture<Void> deserializeInput(Job job) {
        var dataStream = job.request().getDataStream();
        if (dataStream.contentLength() > 0 && !payloadMediaType.equals(dataStream.contentType())) {
            throw MalformedRequestException.builder().message("Invalid content type").build();
        }

        var input = codec().deserializeShape(dataStream.asByteBuffer(),
                job.operation().getApiOperation().inputBuilder());
        job.request().setDeserializedValue(input);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
        var sink = new ByteBufferOutputStream();
        try (var serializer = codec().createSerializer(sink)) {
            output.serialize(serializer);
        }
        job.response().setSerializedValue(DataStream.ofByteBuffer(sink.toByteBuffer(), payloadMediaType));
        var httpJob = job.asHttpJob();
        final int statusCode;
        if (isError) {
            statusCode = ModeledException.getHttpStatusCode(output.schema());
        } else {
            statusCode = 200;
        }
        httpJob.response().headers().setHeader("smithy-protocol", smithyProtocolValue);
        httpJob.response().setStatusCode(statusCode);
        return CompletableFuture.completedFuture(null);
    }

    private boolean matchService(Service service, ServiceAndOperation serviceAndOperation) {
        var schema = service.schema();
        if (serviceAndOperation.isFullyQualifiedService()) {
            return schema.id().toString().equals(serviceAndOperation.service());
        } else {
            return service.schema().id().getName().equals(serviceAndOperation.service());
        }
    }

    // URL path parsing for /service/{ServiceName}/operation/{OperationName}
    // serviceNameStart must be non-negative for any of these offsets to be considered valid
    private static ServiceAndOperation parseRpcV2StylePath(String path) {
        int pos = path.length() - 1;
        int serviceNameStart = -1, serviceNameEnd;
        int operationNameStart = 0, operationNameEnd;
        int namespaceIdx = -1;
        int term = pos + 1;
        operationNameEnd = term;

        for (; pos >= 0; pos--) {
            if (path.charAt(pos) == '/') {
                operationNameStart = pos + 1;
                break;
            }
        }

        // Fail if we went all the way to the start of the path or if the first
        // character encountered is a "/" (e.g. in "/service/foo/operation/")
        if (operationNameStart == 0 || operationNameStart == term || !isValidOperationPrefix(path, pos)) {
            throw UnknownOperationException.builder().message("Invalid RpcV2 URI").build();
        }

        // Seek pos to the character before "/operation", pos is currently on the "n"
        serviceNameEnd = (pos -= 11) + 1;
        for (; pos >= 0; pos--) {
            int c = path.charAt(pos);
            if (c == '/') {
                serviceNameStart = pos + 1;
                break;
            } else if (c == '.' && namespaceIdx < 0) {
                namespaceIdx = pos;
            }
        }

        // Still need "/service" prefix.
        // serviceNameStart < 0 means we never found a "/"
        // serviceNameStart == serviceNameEnd means we had a zero-width name, "/service//"
        if (serviceNameStart < 0 || serviceNameStart == serviceNameEnd || !isValidServicePrefix(path, pos)) {
            throw UnknownOperationException.builder().message("Invalid RpcV2 URI").build();
        }

        String serviceName;
        boolean isFullyQualifiedService;
        if (namespaceIdx > 0) {
            isFullyQualifiedService = true;
            serviceName = path.substring(namespaceIdx + 1, serviceNameEnd);
        } else {
            isFullyQualifiedService = false;
            serviceName = path.substring(serviceNameStart, serviceNameEnd);
        }

        return new ServiceAndOperation(
                serviceName,
                path.substring(operationNameStart, operationNameEnd),
                isFullyQualifiedService);
    }

    // Need 10 chars: "/operation/", pos points to "/"
    // then need another 9 chars for "/service/"
    private static boolean isValidOperationPrefix(String uri, int pos) {
        return pos >= 19 &&
                uri.charAt(pos - 10) == '/'
                &&
                uri.charAt(pos - 9) == 'o'
                &&
                uri.charAt(pos - 8) == 'p'
                &&
                uri.charAt(pos - 7) == 'e'
                &&
                uri.charAt(pos - 6) == 'r'
                &&
                uri.charAt(pos - 5) == 'a'
                &&
                uri.charAt(pos - 4) == 't'
                &&
                uri.charAt(pos - 3) == 'i'
                &&
                uri.charAt(pos - 2) == 'o'
                &&
                uri.charAt(pos - 1) == 'n';
    }

    // Need 8 chars: "/service/", pos points to "/"
    private static boolean isValidServicePrefix(String uri, int pos) {
        return pos >= 8 &&
                uri.charAt(pos - 8) == '/'
                &&
                uri.charAt(pos - 7) == 's'
                &&
                uri.charAt(pos - 6) == 'e'
                &&
                uri.charAt(pos - 5) == 'r'
                &&
                uri.charAt(pos - 4) == 'v'
                &&
                uri.charAt(pos - 3) == 'i'
                &&
                uri.charAt(pos - 2) == 'c'
                &&
                uri.charAt(pos - 1) == 'e';
    }

    private boolean isRpcV2Request(ServiceProtocolResolutionRequest request) {
        if (!"POST".equals(request.method())) {
            return false;
        }
        return smithyProtocolValue.equals(request.headers().firstValue("smithy-protocol"));
    }

    private record ServiceAndOperation(String service, String operation, boolean isFullyQualifiedService) {}
}
