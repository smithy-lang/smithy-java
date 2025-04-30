package software.amazon.smithy.java.example.server.mcp;

import software.amazon.smithy.java.example.server.mcp.operations.GetCodingStatistics;
import software.amazon.smithy.java.example.server.mcp.operations.GetEmployeeDetails;
import software.amazon.smithy.java.example.server.mcp.service.EmployeeService;
import software.amazon.smithy.java.mcp.server.McpServer;

public class MCPServerExample {

    public static void main(String[] args) {
        var service = EmployeeService.builder()
                .addGetCodingStatisticsOperation(new GetCodingStatistics())
                .addGetEmployeeDetailsOperation(new GetEmployeeDetails())
                .build();

        var mcpServer = McpServer.builder()
                .stdio()
                .name("smithy-mcp-server")
                .addService(service)
                .build();

        mcpServer.start();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            mcpServer.shutdown();
        }
    }
}
