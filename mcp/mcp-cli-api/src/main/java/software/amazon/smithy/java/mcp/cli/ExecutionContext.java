package software.amazon.smithy.java.mcp.cli;

import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.mcp.bundle.api.Registry;

public record ExecutionContext(Config config, Registry registry) {
}
