/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class McpServer implements Server {

    private static final InternalLogger LOG = InternalLogger.getLogger(McpServer.class);

    private static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private final McpService mcpService;
    private final Thread listener;
    private final InputStream is;
    private final OutputStream os;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile ProtocolVersion protocolVersion;

    McpServer(McpServerBuilder builder) {
        this.mcpService = builder.mcpService;
        this.is = builder.is;
        this.os = builder.os;
        this.listener = new Thread(() -> {
            try {
                this.listen();
            } catch (Exception e) {
                LOG.error("Error handling request", e);
            } finally {
                done.countDown();
            }
        });
        listener.setName("stdio-dispatcher");
        listener.setDaemon(true);
    }

    private void listen() {
        var scan = new Scanner(is, StandardCharsets.UTF_8);
        while (scan.hasNextLine()) {
            var line = scan.nextLine();
            try {
                var jsonRequest = CODEC.deserializeShape(line, JsonRpcRequest.builder());
                handleRequest(jsonRequest);
            } catch (Exception e) {
                LOG.error("Error decoding request", e);
            }
        }
    }

    private void handleRequest(JsonRpcRequest req) {
        // For StdIO transport, protocol version is only sent in initialize request
        // Extract and store it for future requests
        if ("initialize".equals(req.getMethod())) {
            var maybeVersion = req.getParams().getMember("protocolVersion");
            if (maybeVersion == null) {
                this.protocolVersion = ProtocolVersion.defaultVersion();
            } else {
                this.protocolVersion = ProtocolVersion.version(maybeVersion.asString());
            }
        }

        var response = mcpService.handleRequest(req, this::writeResponse, protocolVersion);
        if (response != null) {
            writeResponse(response);
        }
    }

    private static final byte[] TOOLS_CHANGED = """
            {"jsonrpc":"2.0","method":"notifications/tools/list_changed"}
            """.getBytes(StandardCharsets.UTF_8); // newline is important here

    public void refreshTools() {
        try {
            synchronized (os) {
                os.write(TOOLS_CHANGED);
                os.flush();
            }
        } catch (Exception e) {
            LOG.error("Failed to flush tools changed notification");
        }
    }

    public void addNewService(String id, Service service) {
        mcpService.addNewService(id, service);
        refreshTools();
    }

    public void addNewProxy(McpServerProxy mcpServerProxy) {
        mcpService.addNewProxy(mcpServerProxy, this::writeResponse);
        refreshTools();
    }

    public boolean containsMcpServer(String id) {
        return mcpService.containsMcpServer(id);
    }

    private void writeResponse(JsonRpcResponse response) {
        synchronized (os) {
            try {
                os.write(CODEC.serializeToString(response).getBytes(StandardCharsets.UTF_8));
                os.write('\n');
                os.flush();
            } catch (Exception e) {
                LOG.error("Error encoding response", e);
            }
        }
    }

    @Override
    public void start() {
        // Initialize proxies
        mcpService.startProxies();

        // Start the listener thread
        listener.start();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.allOf(
                mcpService.getProxies()
                        .values()
                        .stream()
                        .map(McpServerProxy::shutdown)
                        .toArray(CompletableFuture[]::new));
    }

    public void awaitCompletion() throws InterruptedException {
        done.await();
    }

    public static McpServerBuilder builder() {
        return new McpServerBuilder();
    }
}
