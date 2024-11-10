/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.rpcv2;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;
import software.amazon.smithy.java.runtime.io.ByteBufferOutputStream;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.Job;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionResult;
import software.amazon.smithy.java.server.exceptions.MalformedHttpException;
import software.amazon.smithy.java.server.exceptions.UnknownOperationException;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait;

final class RpcV2CborProtocol extends ServerProtocol {

    private final Rpcv2CborCodec codec;

    RpcV2CborProtocol(List<Service> services) {
        super(services);
        this.codec = Rpcv2CborCodec.builder().build();
    }

    @Override
    public ShapeId getProtocolId() {
        return Rpcv2CborTrait.ID;
    }

    @Override
    public ServiceProtocolResolutionResult resolveOperation(
        ServiceProtocolResolutionRequest request,
        List<Service> candidates
    ) {
        if (!isRpcV2Request(request)) {
            // This doesn't appear to be an RpcV2 request, let other protocols try.
            return null;
        }
        String path = request.uri().getPath();
        var serviceAndOperation = parseRpcV2StylePath(path);
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
            throw new UnknownOperationException();
        }
        return new ServiceProtocolResolutionResult(
            selectedService,
            selectedService.getOperation(serviceAndOperation.operation),
            this
        );
    }

    @Override
    public CompletableFuture<Void> deserializeInput(Job job) {
        var dataStream = job.request().getDataStream();
        if (dataStream.contentLength() > 0 && !"application/cbor".equals(dataStream.contentType())) {
            throw new MalformedHttpException("Invalid content type");
        }
        return dataStream.asByteBuffer().thenApply(b -> {
            var input = codec.deserializeShape(
                dataStream.waitForByteBuffer(),
                job.operation().getApiOperation().inputBuilder()
            );
            job.request().setDeserializedValue(input);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
        var sink = new ByteBufferOutputStream();
        try (var serializer = codec.createSerializer(sink)) {
            output.serialize(serializer);
            serializer.flush();
        }
        job.response().setSerializedValue(DataStream.ofByteBuffer(sink.toByteBuffer(), "application/cbor"));
        var httpJob = job.asHttpJob();
        final int statusCode;
        if (isError) {
            var httpError = output.schema().getTrait(TraitKey.HTTP_ERROR_TRAIT);
            if (httpError != null) {
                statusCode = httpError.getCode();
            } else {
                var errorTrait = output.schema().getTrait(TraitKey.ERROR_TRAIT);
                if (errorTrait != null && errorTrait.isClientError()) {
                    statusCode = 400;
                } else {
                    statusCode = 500;
                }
            }
        } else {
            statusCode = 200;
        }
        httpJob.response().headers().putHeader("smithy-protocol", "rpc-v2-cbor");
        httpJob.response().setStatusCode(statusCode);
        return CompletableFuture.completedFuture(null);
    }

    private boolean matchService(Service service, ServiceAndOperation serviceAndOperation) {
        var schema = service.schema();
        if (serviceAndOperation.isFullyQualifiedService()) {
            return schema.id().toString().equals(serviceAndOperation.service());
        } else return service.schema().id().getName().equals(serviceAndOperation.service());
    }

    private static ServiceAndOperation parseRpcV2StylePath(String path) {
        // serviceNameStart must be non-negative for any of these offsets
        // to be considered valid
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

        // we could do the same check above the first for loop if we wanted to
        // fail if we went all the way to the start of the path or if the first
        // character encountered is a "/" (e.g. in "/service/foo/operation/")
        if (operationNameStart == 0 || operationNameStart == term || !isValidOperationPrefix(path, pos)) {
            throw new UnknownOperationException("Invalid RpcV2 URI");
        }

        // seek pos to the character before "/operation", pos is currently on the "n"
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

        // still need "/service"
        // serviceNameStart < 0 means we never found a "/"
        // serviceNameStart == serviceNameEnd means we had a zero-width name, "/service//"
        if (serviceNameStart < 0 || serviceNameStart == serviceNameEnd || !isValidServicePrefix(path, pos)) {
            throw new UnknownOperationException("Invalid RpcV2 URI");
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
            isFullyQualifiedService
        );
    }

    private static boolean isValidOperationPrefix(String uri, int pos) {
        // need 10 chars: "/operation/", pos points to "/"
        // then need another 9 chars for "/service/"
        return pos >= 19 &&
            ((uri.charAt(pos - 10) == '/') &&
                (uri.charAt(pos - 9) == 'o') &&
                (uri.charAt(pos - 8) == 'p') &&
                (uri.charAt(pos - 7) == 'e') &&
                (uri.charAt(pos - 6) == 'r') &&
                (uri.charAt(pos - 5) == 'a') &&
                (uri.charAt(pos - 4) == 't') &&
                (uri.charAt(pos - 3) == 'i') &&
                (uri.charAt(pos - 2) == 'o') &&
                (uri.charAt(pos - 1) == 'n'));
    }

    private static boolean isValidServicePrefix(String uri, int pos) {
        // need 8 chars: "/service/", pos points to "/"
        return pos >= 8 &&
            ((uri.charAt(pos - 8) == '/') &&
                (uri.charAt(pos - 7) == 's') &&
                (uri.charAt(pos - 6) == 'e') &&
                (uri.charAt(pos - 5) == 'r') &&
                (uri.charAt(pos - 4) == 'v') &&
                (uri.charAt(pos - 3) == 'i') &&
                (uri.charAt(pos - 2) == 'c') &&
                (uri.charAt(pos - 1) == 'e'));
    }

    private static boolean isRpcV2Request(ServiceProtocolResolutionRequest request) {
        if (!"POST".equals(request.method())) {
            return false;
        }
        return "rpc-v2-cbor".equals(request.headers().firstValue("smithy-protocol"));
    }

    private record ServiceAndOperation(String service, String operation, boolean isFullyQualifiedService) {
    }
}
