$version: "2"

namespace smithy.ai

@unstable
@trait(selector: ":is(service, operation)")
map prompts {
    /// Name of the prompt template
    key: PromptName

    /// Definition of the prompt template
    value: PromptTemplateDefinition
}

@pattern("^[a-zA-Z0-9]+(?:_[a-zA-Z0-9]+)*$")
string PromptName

/// Defines the structure of the prompt
@private
structure PromptTemplateDefinition {
    /// Description of when to use this operation
    @required
    description: String

    /// Template text with placeholders for parameters. A team can also define workflows such as calling multiple API operation.
    @required
    template: String

    /// Optional parameter descriptions to help LLMs understand parameter usage
    arguments: ArgumentShape

    /// Optional condition that describes when this operation should be preferred
    preferWhen: String
}

@private
@idRef(failWhenMissing: true, selector: "structure")
string ArgumentShape
