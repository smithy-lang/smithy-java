$version: "2"

metadata shapeClosures = [
    // A closure that only includes shapes tagged as events.
    {
        id: "smithy.example.birds#events"
        includeBySelector: "[trait|tags|(values) = event]"
    }
]

namespace smithy.example.birds

/// Reports an unclassified potential sighting of a bird from a video stream,
/// detected by a simple computer vision application.
///
/// These events are sent to a queue where a more robust algorithm is applied
/// to verify the sighting and classify the species.
///
/// This is an internal event.
@internal
@tags(["event"])
structure UnclassifiedStreamSighting {
    /// The ID of the camera stream where the bird was detected.
    @required
    streamId: UUID

    /// The timestamp of the stream where the bird was first detected.
    @required
    start: Timestamp

    /// The timestamp of the stream where the bird was last detected.
    @required
    end: Timestamp
}

/// Reports a sighting from a stream that has been classified.
///
/// If confidence is high, these may be automatically added to the list
/// of verified sightings. Otherwise these are sent to a queue for
/// review.
///
/// This is an internal event.
@internal
@tags(["event"])
structure ClassifiedStreamSighting {
    /// The ID of the camera stream where the bird was detected.
    @required
    streamId: UUID

    /// The timestamp of the stream where the bird was first detected.
    @required
    start: Timestamp

    /// The timestamp of the stream where the bird was last detected.
    @required
    end: Timestamp

    /// The proposed classification of the bird.
    @required
    classification: Classification

    /// The confidence in the classification as a percentage.
    @required
    @range(min: 0, max: 100)
    confidence: Float
}

/// Reports an unverified sighting submitted by a user.
///
/// These are sent to a queue for review.
///
/// This is an internal event
@internal
@tags(["event"])
@references([
    {
        resource: Bird
    }
    {
        resource: Sighting
    }
])
structure UnverifiedSighting for Sighting {
    @required
    $birdId

    @required
    $sightingId
}

// This is a public event.
/// Reports a verified sighting of a bird.
@tags(["event"])
@references([
    {
        resource: Bird
    }
    {
        resource: Sighting
    }
])
structure VerifiedSighting for Sighting {
    @required
    $birdId

    @required
    classification: Classification

    @required
    $sightingId

    @required
    $timestamp

    @required
    $location

    @required
    $verified
}
