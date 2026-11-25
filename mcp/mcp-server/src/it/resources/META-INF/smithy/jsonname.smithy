$version: "2"

namespace smithy.example.jsonname

use smithy.ai#mcpHeader
use smithy.mcp#oneOf

service TagService {
    operations: [ListTagsPlain, ListTagsWithTimestamp, ListTagsInRegion]
}

list StringList {
    member: String
}

/// No member needs document adaptation.
operation ListTagsPlain {
    input := {
        @jsonName("tag_names")
        tagNames: StringList

        pageSize: Integer
    }
    output := {
        @required
        @jsonName("is_complete")
        isComplete: Boolean

        nextToken: String
    }
}

/// Same as ListTagsPlain, plus one timestamp member in the input and in the output.
operation ListTagsWithTimestamp {
    input := {
        @jsonName("tag_names")
        tagNames: StringList

        pageSize: Integer

        since: Timestamp
    }
    output := {
        @required
        @jsonName("is_complete")
        isComplete: Boolean

        nextToken: String

        generatedAt: Timestamp
    }
}

/// A top-level input member that is mirrored into an HTTP header and carries a @jsonName.
operation ListTagsInRegion {
    input := {
        @mcpHeader("Tag-Region")
        @jsonName("tag_region")
        tagRegion: String

        weight: BigDecimal

        detail: Document
    }
    output := {
        nextToken: String

        detail: TagDetail

        // Makes the variant reachable for code generation.
        regionDetail: RegionDetail
    }
}

@oneOf(
    discriminator: "__type"
    members: [
        {name: "region", target: RegionDetail}
    ]
)
document TagDetail

structure RegionDetail {
    @jsonName("zone_name")
    zoneName: String
}
