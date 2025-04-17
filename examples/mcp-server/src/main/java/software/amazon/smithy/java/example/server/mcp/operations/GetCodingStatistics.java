package software.amazon.smithy.java.example.server.mcp.operations;

import software.amazon.smithy.java.example.server.mcp.model.GetCodingStatisticsInput;
import software.amazon.smithy.java.example.server.mcp.model.GetCodingStatisticsOutput;
import software.amazon.smithy.java.example.server.mcp.model.NoSuchUserException;
import software.amazon.smithy.java.example.server.mcp.service.GetCodingStatisticsOperation;
import software.amazon.smithy.java.server.RequestContext;

import java.util.Map;

public class GetCodingStatistics implements GetCodingStatisticsOperation {

    @Override
    public GetCodingStatisticsOutput getCodingStatistics(GetCodingStatisticsInput input, RequestContext context) {
        return switch (input.getLogin()) {
            case "janedoe" -> GetCodingStatisticsOutput.builder().commits(Map.of()).build();
            case "johndoe" -> GetCodingStatisticsOutput.builder().commits(Map.of("Java", 100)).build();
            default -> throw NoSuchUserException.builder().message("User doesn't exist in the system").build();
        };
    }
}
