$version: "2"

namespace smithy.example.mcp

use aws.protocols#restJson1
use smithy.ai#prompts

@restJson1
@prompts({
    get_employee_info: {
        description: "Retrieve detailed information about an employee by their login ID"
        template: "Get employee details for login ID {{loginId}}. This will return the employee's name and manager information."
        arguments: GetEmployeeDetailsInput
        preferWhen: "User needs to look up employee information, find who someone reports to, or verify employee details"
    }
    get_coding_stats: {
        description: "Retrieve coding statistics and commit information for an employee"
        template: "Get coding statistics for employee {{login}}. This returns commit counts by programming language."
        arguments: GetCodingStatisticsInput
        preferWhen: "User wants to analyze developer productivity, review coding activity, or understand technology usage patterns"
    }
    Test_employee: {
        description: "General employee lookup and information retrieval service"
        template: "This service provides employee information including personal details and coding statistics. Use get_employee_info for basic details or get_coding_stats for development metrics."
        preferWhen: "User needs any employee-related information or wants to understand available employee data"
    }
})
service EmployeeService {
    operations: [
        GetEmployeeDetails
        GetCodingStatistics
    ]
}

@prompts({
    get_employee_info_operation: {
        description: "Retrieve detailed information about an employee by their login ID on the operation."
        template: "Get employee details for login ID {{loginId}}. This will return the employee's name and manager information."
        arguments: GetEmployeeDetailsInput
        preferWhen: "User needs to look up employee information, find who someone reports to, or verify employee details"
    }
})
@documentation("Get employee information by login id")
@http(method: "POST", uri: "/get-employee-details")
operation GetEmployeeDetails {
    input: GetEmployeeDetailsInput
    output: Employee
    errors: [
        NoSuchUserException
    ]
}

structure GetEmployeeDetailsInput {
    @required
    loginId: LoginId
}

structure Employee {
    @documentation("Name of the employee.")
    name: String

    @documentation("Login id of the employee's manager. A null/missing value indicates that the employee has no manager.")
    managerAlias: String
}

@documentation("Get coding statistics of an employee.")
@http(method: "POST", uri: "/get-coding-statistics")
operation GetCodingStatistics {
    input: GetCodingStatisticsInput
    output: CodingStatistics
}

structure GetCodingStatisticsInput {
    @required
    login: LoginId
}

@documentation("Coding statistics of a user.")
structure CodingStatistics {
    @documentation("Map of number of commits made per language. This can be empty or null.")
    commits: CommitMap
}

@error("client")
structure NoSuchUserException {
    message: String
}

@documentation("Login id of the employee. This is always composed of lower case letters.")
string LoginId

map CommitMap {
    @documentation("Language")
    key: String

    @documentation("Number of commits")
    value: Integer
}
