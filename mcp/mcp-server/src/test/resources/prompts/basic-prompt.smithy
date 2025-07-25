namespace com.example

use amazon.smithy.llm#prompts

@prompts({
    test_prompt: {
        description: "Example Prompt",
        template: "TestTemplate {{requiredDocumentedField}}",
        arguments: TestArgument,
        preferWhen : "TestString"
    }
})
service UserService {
    operations: [
        SearchUsers
    ]
}

structure TestArgument {
    /// test documentation for argument
    @required
    requiredDocumentedField : String
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

