$version: "2"

namespace software.amazon.smithy.java.mcp.conformance

use smithy.ai#mcpHeader
use smithy.ai#prompts

@prompts({
    test_simple_prompt: {
        description: "A simple prompt without arguments"
        template: "This is a simple prompt for testing."
    }
    test_prompt_with_arguments: {
        description: "A prompt with required arguments"
        template: "Prompt with arguments: arg1='{{arg1}}', arg2='{{arg2}}'"
        arguments: TestPromptArguments
    }
})
service ConformanceService {
    operations: [
        TestCustomHeader,
        TestErrorHandling,
        TestMissingCapability,
        TestStreamingElicitation,
        TestLoggingTool,
        TestSimpleText
    ]
}

operation TestCustomHeader {
    input: TestCustomHeaderInput
    output: ConformanceTextOutput
}

operation TestErrorHandling {
    input := {}
    output: ConformanceTextOutput
}

operation TestMissingCapability {
    input := {}
    output: ConformanceTextOutput
}

operation TestStreamingElicitation {
    input := {}
    output: ConformanceTextOutput
}

operation TestLoggingTool {
    input := {}
    output: ConformanceTextOutput
}

operation TestSimpleText {
    input := {}
    output: ConformanceTextOutput
}

structure ConformanceTextOutput {
    @required
    text: String

    // Keeps the prompt argument schema reachable from generated runtime schemas.
    promptArguments: TestPromptArguments
}

structure TestPromptArguments {
    @required
    @documentation("First test argument")
    arg1: String

    @required
    @documentation("Second test argument")
    arg2: String
}

structure TestCustomHeaderInput {
    @required
    @mcpHeader("test-value")
    value: String
}
