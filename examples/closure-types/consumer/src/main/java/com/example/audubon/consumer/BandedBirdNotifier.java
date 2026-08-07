/*
 * Example license header.
 * File header line two
 */

package com.example.audubon.consumer;

import com.example.audubon.events.model.SightingReported;
import com.example.audubon.events.model.SightingWithdrawn;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Predicate;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.serde.Codec;

/**
 * Notifies an ornithologist when a banded bird is sighted and nobody has read its band
 * yet.
 *
 * <p>Decodes events with the same generated types the service encoded them with, so
 * nothing here parses a map or casts an untyped value.
 */
public final class BandedBirdNotifier {

    // Matches the publisher.
    private static final Codec CODEC = Rpcv2CborCodec.builder().build();

    private final Predicate<String> hasBand;
    private final Consumer<String> sendSms;

    /**
     * @param hasBand decides whether a photo shows a banded bird; a photo can show that
     *     a band is present but not what it says, since the code wraps around the leg.
     */
    public BandedBirdNotifier(Predicate<String> hasBand, Consumer<String> sendSms) {
        this.hasBand = hasBand;
        this.sendSms = sendSms;
    }

    /**
     * Handles one message from the subscription. An SNS filter policy could reject
     * unwanted events before they arrive.
     *
     * @param subject the event's shape name
     * @param message a Base64-encoded CBOR payload
     */
    public void onMessage(String subject, String message) {
        byte[] payload = Base64.getDecoder().decode(message);

        switch (subject) {
            case "SightingReported" -> onSightingReported(
                    CODEC.deserializeShape(payload, SightingReported.builder()));
            case "SightingWithdrawn" -> onSightingWithdrawn(
                    CODEC.deserializeShape(payload, SightingWithdrawn.builder()));
            // Ignoring unknown events keeps working when the service adds new ones.
        }
    }

    private void onSightingReported(SightingReported event) {
        // Worth a trip only if a band is visible and nobody has read it yet.
        if (event.getBandCode() != null || event.getPhotoUrl() == null
                || !hasBand.test(event.getPhotoUrl())) {
            return;
        }

        sendSms.accept("Bird " + event.getBirdId() + " sighted at "
                + event.getLocation().getLatitude() + ", " + event.getLocation().getLongitude()
                + " wearing an unread band.");
    }

    private void onSightingWithdrawn(SightingWithdrawn event) {
        sendSms.accept("Sighting " + event.getSightingId() + " was withdrawn.");
    }
}
