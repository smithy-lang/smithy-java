$version: "2"

metadata shapeClosures = [
    {
        id: "com.example.audubon#events"
        includeBySelector: "structure[trait|tags|(values) = event]"
    }
]

namespace com.example.audubon

/// Published when a member reports a new sighting.
@tags(["event"])
@references([{resource: SightingResource}])
structure SightingReported {
    /// The sighting that was reported.
    @required
    sightingId: Uuid

    /// The bird that was sighted.
    @required
    birdId: Uuid

    /// When the bird was sighted.
    @required
    sightedAt: Timestamp

    /// Where the bird was sighted.
    @required
    location: Coordinates

    /// A URL to the photo submitted with the sighting, if there was one.
    photoUrl: String

    /// The code on the bird's identification band, if the member read one.
    bandCode: String
}

/// Published when a sighting is withdrawn.
@tags(["event"])
@references([{resource: SightingResource}])
structure SightingWithdrawn {
    /// The sighting that was withdrawn.
    @required
    sightingId: Uuid

    /// The bird the withdrawn sighting referred to.
    @required
    birdId: Uuid
}
