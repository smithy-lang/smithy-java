$version: "2.0"

namespace com.example.test.unique

use smithy.ai#prompts

// Test model for validating that unique prompt names do not trigger validation errors
@prompts(
    "search_users": {
        template: "Search for users in the system"
        description: "Service-level search template"
    }
    "list_items": {
        template: "List all items"
        description: "List template"
    }
)
service TestService {
    operations: [SearchOperation]
}

@prompts(
    "get_details": {
        template: "Get detailed information"
        description: "Operation-level details template"
    }
)
operation SearchOperation {
    input := {}
    output := {}
}
