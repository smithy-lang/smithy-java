/*
 * Example license header.
 * File header line two
 */

package com.example.audubon.server;

import com.example.audubon.server.model.GetSightingOutput;
import com.example.audubon.server.model.ListSightingsOutput;
import com.example.audubon.server.model.ReportSightingOutput;
import com.example.audubon.server.model.Sighting;
import com.example.audubon.server.model.SightingNotFound;
import com.example.audubon.server.model.SightingReported;
import com.example.audubon.server.model.SightingWithdrawn;
import com.example.audubon.server.model.WithdrawSightingOutput;
import com.example.audubon.server.service.GetSightingOperation;
import com.example.audubon.server.service.ListSightingsOperation;
import com.example.audubon.server.service.ReportSightingOperation;
import com.example.audubon.server.service.WithdrawSightingOperation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementations of the four BirdWatcher operations.
 *
 * <p>Sightings live in a map. A real service would use a durable store such as Amazon
 * DynamoDB, keyed on the sighting ID with a secondary index on {@code birdId}.
 */
public final class BirdWatcherHandlers {

    private final Map<String, Sighting> sightings = new ConcurrentHashMap<>();
    private final EventPublisher events;

    public BirdWatcherHandlers(EventPublisher events) {
        this.events = events;
    }

    /**
     * Stores the sighting and announces it. No operation returns
     * {@code SightingReported}, so without a shape closure it would have no type.
     */
    public ReportSightingOperation reportSighting() {
        return (input, context) -> {
            String sightingId = UUID.randomUUID().toString();

            // A real service would upload to Amazon S3. Events carry the URL, not bytes.
            String photoUrl = input.getPhoto() == null
                    ? null
                    : "https://photos.example.com/audubon/" + sightingId + ".jpg";

            Sighting sighting = Sighting.builder()
                    .sightingId(sightingId)
                    .birdId(input.getBirdId())
                    .sightedAt(input.getSightedAt())
                    .location(input.getLocation())
                    .photoUrl(photoUrl)
                    .bandCode(input.getBandCode())
                    .build();
            sightings.put(sightingId, sighting);

            events.publish(SightingReported.builder()
                    .sightingId(sightingId)
                    .birdId(sighting.getBirdId())
                    .sightedAt(sighting.getSightedAt())
                    .location(sighting.getLocation())
                    .photoUrl(sighting.getPhotoUrl())
                    .bandCode(sighting.getBandCode())
                    .build());

            return ReportSightingOutput.builder().sightingId(sightingId).build();
        };
    }

    public GetSightingOperation getSighting() {
        return (input, context) -> {
            Sighting sighting = require(input.getSightingId());
            return GetSightingOutput.builder()
                    .sightingId(sighting.getSightingId())
                    .birdId(sighting.getBirdId())
                    .sightedAt(sighting.getSightedAt())
                    .location(sighting.getLocation())
                    .photoUrl(sighting.getPhotoUrl())
                    .bandCode(sighting.getBandCode())
                    .build();
        };
    }

    public ListSightingsOperation listSightings() {
        return (input, context) -> ListSightingsOutput.builder()
                .sightings(List.copyOf(sightings.values()))
                .build();
    }

    /** Deletes the sighting and announces it, so subscribers can discard it. */
    public WithdrawSightingOperation withdrawSighting() {
        return (input, context) -> {
            Sighting withdrawn = require(input.getSightingId());
            sightings.remove(withdrawn.getSightingId());

            events.publish(SightingWithdrawn.builder()
                    .sightingId(withdrawn.getSightingId())
                    .birdId(withdrawn.getBirdId())
                    .build());

            return WithdrawSightingOutput.builder().build();
        };
    }

    /** Returns the sighting, or throws the modeled error. */
    private Sighting require(String sightingId) {
        Sighting sighting = sightings.get(sightingId);
        if (sighting == null) {
            throw SightingNotFound.builder().message("No sighting " + sightingId).build();
        }
        return sighting;
    }
}
