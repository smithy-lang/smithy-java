package software.amazon.smithy.java.example.server.mcp;

import software.amazon.smithy.java.example.server.mcp.operations.GetCodingStatistics;
import software.amazon.smithy.java.example.server.mcp.operations.GetEmployeeDetails;
import software.amazon.smithy.java.example.server.mcp.service.EmployeeService;
import software.amazon.smithy.java.mcp.server.StdioMcpServer;

public class MCPServerExample {

    public static void main(String[] args) {
        var service = EmployeeService.builder()
                .addGetCodingStatisticsOperation(new GetCodingStatistics())
                .addGetEmployeeDetailsOperation(new GetEmployeeDetails())
                .build();

        var mcpServer = StdioMcpServer.builder()
                .stdio()
                .name("smithy-mcp-server")
                .addService("employee-mcp", service)
                .build();

        mcpServer.start();

        try {
            mcpServer.awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mcpServer.shutdown();
        }
    }
}
