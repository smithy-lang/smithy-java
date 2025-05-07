package software.amazon.smithy.java.example.server.mcp;

import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.Bundles;

import java.util.Objects;

public final class BundleMCPServerExample {
    public static void main(String[] args) throws Exception {
        try {
            var bundle = loadBundle("dynamodb.json");
            var mcpServer = McpServer.builder()
                .stdio()
                .name("smithy-mcp-server")
                .addService(Bundles.getService(bundle.getValue()))
                .build();

            mcpServer.start();

            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                mcpServer.shutdown().join();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static Bundle loadBundle(String name) throws Exception {
        var codec = JsonCodec.builder().build();
        var payload = Objects.requireNonNull(BundleMCPServerExample.class.getResourceAsStream(name),
            "no bundle named " + name).readAllBytes();
        try (var reader = codec.createDeserializer(payload)) {
            return Bundle.builder().deserialize(reader).build();
        }
    }
}