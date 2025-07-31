$version: "2.0"

namespace com.example.test.service

use smithy.ai#prompts

// Test model for validating service-level prompt name conflicts
@prompts(
    "search_users": {
        template: "Search for users in the system"
        description: "Service-level search template"
    }
    "Search_Users": {
        template: "DUPLICATE: Search for users again (intentional case conflict)"
        description: "Duplicate search template with different case for testing validation"
    }
)
service TestService {
    operations: []
}
