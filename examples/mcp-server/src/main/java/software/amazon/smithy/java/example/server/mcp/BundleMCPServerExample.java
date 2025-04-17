package software.amazon.smithy.java.example.server.mcp;

import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.server.ProxyService;
import software.amazon.smithy.java.server.mcp.MCPServer;
import software.amazon.smithy.modelbundle.api.model.Bundle;

import java.util.Objects;

public final class BundleMCPServerExample {
    public static void main(String[] args) throws Exception {
        try {
            var mcpServer = MCPServer.builder()
                .stdio()
                .name("smithy-mcp-server")
                .addService(
                    ProxyService.builder()
                        .bundle(loadBundle("dynamodb.json"))
                        .build())
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