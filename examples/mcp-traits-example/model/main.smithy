namespace com.example

use smithy.ai#prompts

@prompts({
    search_users: {
        description: "Search for users in the system by various criteria"
        template: "Search for users where {{searchCriteria}}. Use pagination with limit={{limit}} if many results expected."
        arguments: SearchUsersInput
        preferWhen: "User wants to find specific users or browse user lists"
    }
})
service UserService {
    operations: [
        SearchUsers
    ]
}

operation SearchUsers {
    input: SearchUsersInput
    output: SearchUsersOutput
}

structure SearchUsersOutput {
    id: String
}

structure SearchUsersInput {
    searchCriteria: String
    limit: Integer
}
