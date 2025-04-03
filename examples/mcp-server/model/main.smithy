$version: "2"

namespace smithy.example

use aws.protocols#restJson1

@restJson1
service EmployeeService {
    operations: [
        GetEmployeeDetails
    ]
}

@http(method: "POST", uri: "/get-user-details")
@documentation("Get employee information by login (alias)")
@examples([
    {
        title: "Invoke GetEmployeeDetails"
        input: { alias: "janedoe" }
        output: { name: "Jane Doe", managerAlias: "roger" }
    }
])
operation GetEmployeeDetails {
    input := {
        @documentation("Login alias of the employee. Alias is always composed of lower case letters.")
        @required
        alias: String
    }

    output: Employee

    errors: [
        NoSuchUserException
    ]
}

structure Employee {
    @length(min: 3)
    name: String

    managerAlias: String
}

@error("client")
structure NoSuchUserException {
    message: String
}
