$version: "2"

namespace smithy.java.codegen.integrations.javadoc

service TestService {
    version: "today",
    operations: [
        DocStringWrapping
        SmithyGenerated
        Deprecated
        Since
        ExternalDocumentation
        Unstable
        Rollup
    ]
}

operation DocStringWrapping {
    input := {
        /// This is a long long docstring that should be wrapped. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
        shouldBeWrapped: String

        /// Documentation includes preformatted text that should not be messed with.
        /// For example:
        /// <pre>
        /// Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
        /// </pre>
        ///
        /// <ul>
        ///     <li> Lorem ipsum dolor sit amet, consectetur adipiscing elit Lorem ipsum dolor sit amet, consectetur adipiscing elit </li>
        ///     <li> Lorem ipsum dolor sit amet, consectetur adipiscing elit Lorem ipsum dolor sit amet, consectetur adipiscing elit </li>
        /// </ul>
        shouldNotBeWrapped: String
    }
}

operation SmithyGenerated {
    input := {}
}

operation Deprecated {
    input: DeprecatedInput
}

@deprecated(since: "1.3")
structure DeprecatedInput {
    @deprecated(since: "1.2")
    deprecatedMember: String

    @deprecated
    deprecatedMemberNoSince: String

    /// Has docs in addition to deprecated
    @deprecated
    deprecatedWithDocs: String
}

operation Since {
    input: SinceInput
}

@since("4.5")
structure SinceInput {
    @since("1.2")
    sinceMember: String
}

operation ExternalDocumentation {
    input: ExternalDocumentationInput
}

@externalDocumentation(
    "Auks are cool birds": "https://en.wikipedia.org/wiki/Auk"
    "Example": "https://example.com"
)
structure ExternalDocumentationInput {
    @externalDocumentation(
        "Puffins are also neat": "https://en.wikipedia.org/wiki/Puffin"
    )
    memberWithExternalDocumentation: String
}

operation Unstable {
    input : UnstableInput
}

@unstable
structure UnstableInput {
    @unstable
    unstableMember: String
}

operation Rollup {
    input: RollupInput
}

/// This structure applies all documentation traits
@unstable
@deprecated(since: "sometime")
@externalDocumentation(
    "Puffins are still cool": "https://en.wikipedia.org/wiki/Puffin"
)
@since("4.5")
structure RollupInput {
    /// This member applies all documentation traits
    @unstable
    @deprecated(since: "sometime")
    @externalDocumentation(
        "Puffins are still cool": "https://en.wikipedia.org/wiki/Puffin"
    )
    @since("4.5")
    rollupMember: String
}
