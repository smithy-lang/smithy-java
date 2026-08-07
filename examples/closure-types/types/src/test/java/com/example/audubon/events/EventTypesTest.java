/*
 * Example license header.
 * File header line two
 */

package com.example.audubon.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.audubon.events.model.Coordinates;
import com.example.audubon.events.model.SightingReported;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.serde.Codec;

/**
 * The event types generate from the closure even though no operation refers to them,
 * and they round-trip through a codec.
 */
class EventTypesTest {

    private final Codec codec = Rpcv2CborCodec.builder().build();

    @Test
    void roundTripsAnEvent() {
        SightingReported event = SightingReported.builder()
                .sightingId("sighting-1")
                .birdId("bird-1")
                .sightedAt(Instant.EPOCH)
                .location(Coordinates.builder().latitude(1.0).longitude(2.0).build())
                .build();

        SightingReported decoded = codec.deserializeShape(codec.serialize(event), SightingReported.builder());

        assertThat(decoded).isEqualTo(event);
        // Optional members are absent rather than defaulted.
        assertThat(decoded.getBandCode()).isNull();
    }

    @Test
    void generatesNoServiceTypes() {
        // Types mode has no service, so operation shapes are not generated.
        assertThatThrownBy(() -> Class.forName("com.example.audubon.events.model.ReportSightingInput"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
