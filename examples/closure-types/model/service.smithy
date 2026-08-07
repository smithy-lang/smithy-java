$version: "2"

metadata shapeClosures = [
    // Everything in the namespace: the service and the events it publishes. Combined
    // mode requires the primary service to be a member of the closure it generates,
    // so the server subproject drives generation from this one.
    {
        id: "com.example.audubon#all"
        includeNamespaces: ["com.example.audubon"]
    }
]

namespace com.example.audubon

use smithy.protocols#rpcv2Cbor

/// Tracks bird sightings reported by members of a bird-watching club.
@rpcv2Cbor
service BirdWatcher {
    version: "2026-08-05"
    resources: [
        SightingResource
    ]
    errors: [
        SightingNotFound
    ]
}

/// A single report of a bird, submitted by a club member.
resource SightingResource {
    identifiers: {
        sightingId: Uuid
    }
    properties: {
        birdId: Uuid
        sightedAt: Timestamp
        location: Coordinates
        photoUrl: String
        bandCode: String
    }
    create: ReportSighting
    read: GetSighting
    list: ListSightings
    delete: WithdrawSighting
}

/// A sighting as the service stores and returns it.
structure Sighting for SightingResource {
    /// The identifier assigned to this sighting.
    @required
    $sightingId

    /// The bird that was sighted.
    @required
    $birdId

    /// When the bird was sighted.
    @required
    $sightedAt

    /// Where the bird was sighted.
    @required
    $location

    /// A URL to the photo submitted with the sighting, if there was one.
    $photoUrl

    /// The code on the bird's identification band, if the member read one.
    $bandCode
}

/// Records a member's sighting of a bird.
operation ReportSighting {
    input := for SightingResource {
        /// The bird that was sighted.
        @required
        $birdId

        /// When the bird was sighted.
        @required
        $sightedAt

        /// Where the bird was sighted.
        @required
        $location

        /// A photo of the bird.
        @notProperty
        photo: Photo

        /// The code on the bird's identification band, if the member read one.
        $bandCode
    }

    output := for SightingResource {
        /// The identifier assigned to the new sighting.
        @required
        $sightingId
    }
}

/// Retrieves a single sighting.
@readonly
operation GetSighting {
    input := for SightingResource {
        @required
        $sightingId
    }

    output := for SightingResource {
        @required
        $sightingId

        @required
        $birdId

        @required
        $sightedAt

        @required
        $location

        $photoUrl

        $bandCode
    }
}

/// Lists every sighting.
@readonly
operation ListSightings {
    input := {}

    output := {
        @required
        sightings: Sightings
    }
}

/// Withdraws a sighting that was reported in error.
///
/// The sighting is deleted. Subscribers that acted on it find out through the
/// `SightingWithdrawn` event.
@idempotent
operation WithdrawSighting {
    input := for SightingResource {
        @required
        $sightingId
    }

    output := {}
}

list Sightings {
    member: Sighting
}

/// Returned when no sighting has the requested identifier.
@error("client")
structure SightingNotFound {
    @required
    message: String
}

/// Where a sighting took place.
structure Coordinates {
    @required
    latitude: Double

    @required
    longitude: Double
}

/// A UUID, used for every identifier in this model.
@pattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
string Uuid

/// A JPEG photo of a bird.
@mediaType("image/jpeg")
blob Photo
