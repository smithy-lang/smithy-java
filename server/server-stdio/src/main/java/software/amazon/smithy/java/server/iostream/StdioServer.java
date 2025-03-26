/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.iostream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.framework.model.UnknownOperationException;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.jsonrpc.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.jsonrpc.model.JsonRpcRequest;
import software.amazon.smithy.java.jsonrpc.model.JsonRpcResponse;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.core.ErrorHandlingOrchestrator;
import software.amazon.smithy.java.server.core.HandlerAssembler;
import software.amazon.smithy.java.server.core.OrchestratorGroup;
import software.amazon.smithy.java.server.core.ProtocolResolver;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.core.SingleThreadOrchestrator;
import software.amazon.smithy.java.server.core.StdioJob;
import software.amazon.smithy.java.server.core.StdioRequest;
import software.amazon.smithy.java.server.core.StdioResponse;

final class StdioServer implements Server {
    private static final InternalLogger LOG = InternalLogger.getLogger(StdioServer.class);

    private final StdioDispatcher dispatcher;
    private final Thread listener;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ProtocolResolver resolver;
    private final InputStream is;
    private final OutputStream os;

    StdioServer(IOStreamServerBuilder builder) {
        var handlers = new HandlerAssembler().assembleHandlers(builder.serviceMatcher.getAllServices());
        var orchestrator = new OrchestratorGroup(
                builder.numberOfWorkers,
                () -> new ErrorHandlingOrchestrator(new SingleThreadOrchestrator(handlers)),
                OrchestratorGroup.Strategy.roundRobin());
        this.resolver = new ProtocolResolver(builder.serviceMatcher);
        this.dispatcher = new StdioDispatcher(orchestrator, resolver);
        this.is = builder.is;
        this.os = builder.os;
        listener = new Thread(() -> {
            try {
                dispatcher.listen(is, os);
            } catch (Exception e) {
                LOG.error("Error handling request", e);
            }
        });
        listener.setName("stdio-dispatcher");
        listener.setDaemon(true);
    }

    @Override
    public void start() {
        listener.start();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        active.set(false);
        return dispatcher.stop();
    }

    static final class StdioDispatcher {
        private static final JsonCodec CODEC = JsonCodec.builder()
                .settings(JsonSettings.builder()
                        .serializeTypeInDocuments(false)
                        .build())
                .build();
        private static final URI DUMMY_URI = URI.create("/");
        private static final HttpHeaders NO_HEADERS = HttpHeaders.of(Collections.emptyMap());
        private static final int UNKNOWN_METHOD = -32601;

        private final OrchestratorGroup orchestrator;
        private final ProtocolResolver resolver;

        StdioDispatcher(OrchestratorGroup orchestrator, ProtocolResolver resolver) {
            this.orchestrator = orchestrator;
            this.resolver = resolver;
        }

        // visible for testing
        void listen(InputStream is, OutputStream os) {
            var scan = new Scanner(is, StandardCharsets.UTF_8);
            while (scan.hasNextLine()) {
                var line = scan.nextLine();
                try {
                    var jsonRequest = CODEC.deserializeShape(line, JsonRpcRequest.builder());
                    var smithyRequest = new StdioRequest(jsonRequest);
                    var smithyResponse = new StdioResponse();
                    StdioJob job;
                    try {
                        var op = resolver.resolve(new ServiceProtocolResolutionRequest(DUMMY_URI,
                                NO_HEADERS,
                                smithyRequest.context(),
                                jsonRequest.method()));
                        job = new StdioJob(op.operation(), op.protocol(), smithyRequest, smithyResponse);
                    } catch (UnknownOperationException e) {
                        unknownOperation(jsonRequest, os);
                        continue;
                    }

                    orchestrator.enqueue(job).whenComplete((res, err) -> {
                        if (err != null) {
                            LOG.error("Error handling request", err);
                        } else {
                            writeResponse(smithyRequest.id(), smithyResponse, os);
                        }
                    });
                } catch (Exception e) {
                    LOG.error("Error decoding request", e);
                }
            }
        }

        private void unknownOperation(JsonRpcRequest request, OutputStream os) {
            var error = JsonRpcErrorResponse.builder()
                    .code(UNKNOWN_METHOD)
                    .message("Unknown operation: " + request.method())
                    .build();
            var response = JsonRpcResponse.builder()
                    .id(request.id())
                    .error(error)
                    .jsonrpc("2.0")
                    .build();
            synchronized (os) {
                try {
                    os.write(CODEC.serializeToString(response).getBytes(StandardCharsets.UTF_8));
                    os.write('\n');
                } catch (Exception e) {
                    LOG.error("Error encoding response", e);
                }
            }
        }

        private void writeResponse(int id, StdioResponse smithyResponse, OutputStream os) {
            var response = JsonRpcResponse.builder()
                    .id(id)
                    .result(Document.of(smithyResponse.response()))
                    .jsonrpc("2.0")
                    .build();
            synchronized (os) {
                try {
                    os.write(CODEC.serializeToString(response).getBytes(StandardCharsets.UTF_8));
                    os.write('\n');
                } catch (Exception e) {
                    LOG.error("Error encoding response", e);
                }
            }
        }

        CompletableFuture<Void> stop() {
            return orchestrator.shutdown();
        }
    }
}
