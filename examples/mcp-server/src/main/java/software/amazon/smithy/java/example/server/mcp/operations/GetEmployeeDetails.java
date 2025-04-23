package software.amazon.smithy.java.example.server.mcp.operations;

import software.amazon.smithy.java.example.server.mcp.model.GetEmployeeDetailsInput;
import software.amazon.smithy.java.example.server.mcp.model.GetEmployeeDetailsOutput;
import software.amazon.smithy.java.example.server.mcp.model.NoSuchUserException;
import software.amazon.smithy.java.example.server.mcp.service.GetEmployeeDetailsOperation;
import software.amazon.smithy.java.server.RequestContext;

public class GetEmployeeDetails implements GetEmployeeDetailsOperation {


    @Override
    public GetEmployeeDetailsOutput getEmployeeDetails(GetEmployeeDetailsInput input, RequestContext context) {
        return switch (input.getLoginId()) {
            case "janedoe" -> GetEmployeeDetailsOutput.builder().name("Jane Doe").build();
            case "johndoe" -> GetEmployeeDetailsOutput.builder().name("John Doe").managerAlias("janedoe").build();
            default -> throw NoSuchUserException.builder().message("User doesn't exist in the system").build();
        };
    }
}
