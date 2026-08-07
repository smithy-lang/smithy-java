/*
 * Example license header.
 * File header line two
 */

package com.example.audubon.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.audubon.events.model.Coordinates;
import com.example.audubon.events.model.SightingReported;
import com.example.audubon.events.model.SightingWithdrawn;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.io.ByteBufferUtils;

/**
 * The subscriber decodes events with the shared generated types, and only alerts about
 * birds worth a trip.
 */
class BandedBirdNotifierTest {

    private static final String BIRD = "bird-1";
    private static final String SIGHTING = "sighting-1";
    private static final Codec CODEC = Rpcv2CborCodec.builder().build();

    private final List<String> sent = new ArrayList<>();

    private static SightingReported.Builder reported() {
        return SightingReported.builder()
                .sightingId(SIGHTING)
                .birdId(BIRD)
                .sightedAt(Instant.EPOCH)
                .location(Coordinates.builder().latitude(1.0).longitude(2.0).build())
                .photoUrl("https://photos.example.com/audubon/1.jpg");
    }

    @Test
    void alertsWhenAPhotoShowsAnUnreadBand() {
        notifier(photoUrl -> true).onMessage("SightingReported", encode(reported().build()));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).contains(BIRD).contains("unread band");
    }

    @Test
    void staysQuietWhenThereIsNothingToGoSee() {
        BandedBirdNotifier notifier = notifier(photoUrl -> true);

        // Already read, so there is no trip to make.
        notifier.onMessage("SightingReported", encode(reported().bandCode("AB-1247").build()));
        // No photo to inspect.
        notifier.onMessage("SightingReported",
                encode(SightingReported.builder()
                        .sightingId(SIGHTING)
                        .birdId(BIRD)
                        .sightedAt(Instant.EPOCH)
                        .location(Coordinates.builder().latitude(1.0).longitude(2.0).build())
                        .build()));
        // An event this subscriber does not handle.
        notifier.onMessage("SomethingElse", encode(reported().build()));

        assertThat(sent).isEmpty();
    }

    @Test
    void retractsAnAlertWhenTheSightingIsWithdrawn() {
        notifier(photoUrl -> true).onMessage("SightingWithdrawn",
                encode(SightingWithdrawn.builder().sightingId(SIGHTING).birdId(BIRD).build()));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).contains("withdrawn");
    }

    private BandedBirdNotifier notifier(Predicate<String> hasBand) {
        return new BandedBirdNotifier(hasBand, sent::add);
    }

    /** Encodes an event the way the service does. */
    private static String encode(SerializableStruct event) {
        return ByteBufferUtils.base64Encode(CODEC.serialize(event));
    }
}
