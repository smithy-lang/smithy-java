$version: "2"

namespace smithy.java.codegen

service TestService {
    version: "today"
    operations: [
        DocStringWrapping
        SmithyGeneratedAnnotation
        DeprecatedAnnotation
        Since
        ExternalDocumentation
        Unstable
        Rollup
        EnumVariants
        UnsupportedTags
        BuilderSetters
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

operation SmithyGeneratedAnnotation {
    input := {}
}

operation DeprecatedAnnotation {
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

@externalDocumentation("Auks are cool birds": "https://en.wikipedia.org/wiki/Auk", Example: "https://example.com")
structure ExternalDocumentationInput {
    @externalDocumentation("Puffins are also neat": "https://en.wikipedia.org/wiki/Puffin")
    memberWithExternalDocumentation: String
}

operation Unstable {
    input: UnstableInput
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
@externalDocumentation("Puffins are still cool": "https://en.wikipedia.org/wiki/Puffin")
@since("4.5")
structure RollupInput {
    /// This member applies all documentation traits
    @unstable
    @deprecated(since: "sometime")
    @externalDocumentation("Puffins are still cool": "https://en.wikipedia.org/wiki/Puffin")
    @since("4.5")
    rollupMember: String
}

/// Checks that docs are correctly added to enum variants
operation EnumVariants {
    input := {
        enum: EnumWithDocs
    }
}

/// Generic Documentation
@since("4.5")
enum EnumWithDocs {
    @deprecated(since: "the past")
    DOCUMENTED

    /// General Docs
    @unstable
    ALSO_DOCUMENTED
}

operation UnsupportedTags {
    input := {
        /// Documentation includes html tags that are not supported by Javadoc. These unsupported blocks should be skipped.
        /// <pre>
        /// This should be included.
        /// </pre>
        /// <note>
        /// Note is not a valid javadoc tag, so this should be skipped.
        /// </note>
        ///
        /// <pre>
        ///     <code>CodeIsSupported</code>
        ///     <important>Important is not supported</important>
        /// </pre>
        value: String
    }
}

operation BuilderSetters {
    input := {
        /// Member with docs
        foo: String

        /// Required Field
        @required
        required: String

        /// Recommended Field
        @recommended
        recommended: String
    }
}
