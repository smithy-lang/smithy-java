package software.amazon.smithy.java.example.server.mcp;

import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.example.server.mcp.operations.GetCodingStatistics;
import software.amazon.smithy.java.example.server.mcp.operations.GetEmployeeDetails;
import software.amazon.smithy.java.example.server.mcp.service.EmployeeService;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.java.server.ProxyService;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.model.Model;

public class ProxyMCPExample {

    public static void main(String[] args) {

        var service = EmployeeService.builder()
                .addGetCodingStatisticsOperation(new GetCodingStatistics())
                .addGetEmployeeDetailsOperation(new GetEmployeeDetails())
                .build();
        Server server = Server.builder()
                .numberOfWorkers(10)
                .endpoints(8080)
                .addService(service)
                .build();

        server.start();

        var model = Model.assembler()
                .addImport(ProxyMCPExample.class.getResource("main.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();

        var mcpService = ProxyService.builder()
                .service(EmployeeService.$ID)
                .model(model)
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .proxyEndpoint("http://localhost:8080")
                .build();

        var mcpServer = McpServer.builder()
                .stdio()
                .name("smithy-mcp-server")
                .addService("employee-mcp", mcpService)
                .build();
        mcpServer.start();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            mcpServer.shutdown();
            server.shutdown();
        }
    }
}
