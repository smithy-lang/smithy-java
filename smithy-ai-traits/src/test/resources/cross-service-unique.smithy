$version: "2.0"

namespace com.example.test.cross

use smithy.ai#prompts

// Test model for validating that same prompt names are allowed across different services
@prompts(
    "search_users": {
        template: "Search for users in ServiceOne"
        description: "ServiceOne search template"
    }
)
service ServiceOne {
    operations: []
}

@prompts(
    "search_users": {
        template: "Search for users in ServiceTwo"
        description: "ServiceTwo search template"
    }
)
service ServiceTwo {
    operations: []
}
