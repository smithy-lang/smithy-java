$version: "2"

metadata shapeClosures = [
    // A closure that includes every shape in the service namespace,
    // including events.
    {
        id: "smithy.example.birds#fullService"
        includeNamespaces: ["smithy.example.birds"]
    }
]

namespace smithy.example.birds

use smithy.protocols#rpcv2Cbor

/// A service that tracks bird sightings for research purposes.
@rpcv2Cbor
@paginated(inputToken: "nextToken", outputToken: "nextToken", pageSize: "pageSize")
service iBird {
    resources: [
        Bird
    ]
}

/// A resource representing the bird itself.
///
/// These may only be created by the service operators.
resource Bird {
    identifiers: {
        birdId: UUID
    }
    properties: {
        classification: Classification
    }
    resources: [
        Sighting
    ]
    create: CreateBird
    read: GetBird
    list: ListBirds
}

/// The taxonomic classification of a bird.
///
/// Ranks above order are shared by all birds, so they are omitted.
structure Classification {
    @required
    order: NonEmptyString

    @required
    family: NonEmptyString

    @required
    genus: NonEmptyString

    @required
    species: NonEmptyString

    subspecies: NonEmptyString
}

/// Adds a bird to the database. This is for internal use only.
@internal
operation CreateBird {
    input := for Bird {
        @required
        $classification
    }
}

/// Retrieves information about a specific bird.
@readonly
operation GetBird {
    input := for Bird {
        @required
        $birdId
    }

    output := for Bird {
        @required
        $birdId

        @required
        $classification
    }
}

/// Lists birds present in the database.
@paginated(items: "birds")
@readonly
operation ListBirds {
    input := with [PaginatedInput] {}

    output := with [PaginatedOutput] {
        @required
        birds: BirdList
    }
}

list BirdList {
    member: BirdSummary
}

/// A summary of a bird's properties.
structure BirdSummary for Bird {
    $birdId
    $classification
}

/// A resource representing a bird sighting.
///
/// These may be created either by user submission or by automated monitoring
/// systems. Sightings are verified before appearing in listings.
resource Sighting {
    identifiers: {
        birdId: UUID
        sightingId: UUID
    }
    properties: {
        timestamp: Timestamp
        location: Coordinates
        image: Image
        verified: Boolean
    }
    create: CreateSighting
    read: GetSighting
    list: ListSightings
}

/// Creates a sighting.
operation CreateSighting {
    input := for Sighting {
        @required
        $birdId

        @required
        $timestamp

        @required
        $location

        @required
        image: Image

        // For internal use only. Automated sightings from a stream may set
        // this to true if their confidence is high.
        @internal
        verified: Boolean
    }

    output := for Sighting {
        @required
        $sightingId
    }
}

/// Gets a sighting.
///
/// Unverified sightings may be retrieved here, even if they don't appear in
/// listings.
@readonly
operation GetSighting {
    input := for Sighting {
        @required
        $birdId

        @required
        $sightingId
    }

    output := for Sighting {
        @required
        $timestamp

        @required
        $location

        @required
        $image

        @required
        $verified
    }
}

/// List verified sightings for a particular bird.
@paginated(items: "sightings")
@readonly
operation ListSightings {
    input := for Bird with [PaginatedInput] {
        @required
        $birdId
    }

    output := with [PaginatedOutput] {
        @required
        sightings: SightingSummaryList
    }
}

/// Geographical coordinates from where a sighting took place.
structure Coordinates {
    latitude: BigDecimal
    longitude: BigDecimal
}

list SightingSummaryList {
    member: SightingSummary
}

/// A summary of a sighting's properties.
structure SightingSummary for Sighting {
    @required
    $birdId

    @required
    $sightingId

    @required
    $timestamp

    @required
    $location

    @required
    $verified
}

// A mixin to share input pagination parameters.
@mixin
@private
structure PaginatedInput {
    nextToken: NonEmptyString

    @range(min: 1, max: 1000)
    pageSize: Integer = 100
}

// A mixin to share output pagination parameters.
@mixin
@private
structure PaginatedOutput {
    nextToken: NonEmptyString
}

/// A UUID-v4 string.
@pattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
string UUID

@length(min: 1)
string NonEmptyString

/// A JPEG image.
@mediaType("image/jpeg")
blob Image
