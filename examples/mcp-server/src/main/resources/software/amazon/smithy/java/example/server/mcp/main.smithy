$version: "2"

namespace smithy.example.mcp

use aws.protocols#restJson1

@restJson1
service EmployeeService {
    operations: [
        GetEmployeeDetails
        GetCodingStatistics
    ]
}

@documentation("Get employee information by login id")
@http(method: "POST", uri: "/get-employee-details")
operation GetEmployeeDetails {
    input := {
        @required
        loginId: LoginId
    }

    output: Employee

    errors: [
        NoSuchUserException
    ]
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
    input := {
        @required
        login: LoginId
    }

    output: CodingStatistics
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
