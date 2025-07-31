$version: "2.0"

namespace com.example.test

use smithy.ai#prompts

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
    "Search_Users": {
        template: "Search for users from operation"
        description: "Operation-level search template (conflicts with service-level)"
    }
)
operation SearchOperation {
    input := {}
    output := {}
}
