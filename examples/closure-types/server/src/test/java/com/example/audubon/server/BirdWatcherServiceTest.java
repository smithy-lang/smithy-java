/*
 * Example license header.
 * File header line two
 */

package com.example.audubon.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.audubon.server.model.Coordinates;
import com.example.audubon.server.model.GetSightingInput;
import com.example.audubon.server.model.ListSightingsInput;
import com.example.audubon.server.model.ReportSightingInput;
import com.example.audubon.server.model.SightingNotFound;
import com.example.audubon.server.model.SightingReported;
import com.example.audubon.server.model.SightingWithdrawn;
import com.example.audubon.server.model.WithdrawSightingInput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.server.Service;

/**
 * Reporting and withdrawing a sighting publish events, using types the shape closure
 * brought into generation.
 */
class BirdWatcherServiceTest {

    private static final String BIRD = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
    private static final Codec CODEC = Rpcv2CborCodec.builder().build();

    private final List<PublishRequest> published = new ArrayList<>();
    private final BirdWatcherHandlers handlers = new BirdWatcherHandlers(
            new EventPublisher(new FakeSnsClient(published), "arn:aws:sns:us-west-2:1:sightings"));

    private String report() {
        return handlers.reportSighting().reportSighting(
                ReportSightingInput.builder()
                        .birdId(BIRD)
                        .sightedAt(Instant.parse("2026-08-05T14:32:00Z"))
                        .location(Coordinates.builder().latitude(47.6062).longitude(-122.3321).build())
                        .build(),
                null)
                .getSightingId();
    }

    @Test
    void buildsAServiceFromTheGeneratedInterfaces() {
        assertThat(BirdWatcherService.create(handlers)).isInstanceOf(Service.class);
    }

    @Test
    void reportingASightingStoresItAndPublishesAnEvent() {
        String id = report();

        assertThat(handlers.getSighting()
                .getSighting(GetSightingInput.builder().sightingId(id).build(), null)
                .getBirdId())
                .isEqualTo(BIRD);
        assertThat(handlers.listSightings()
                .listSightings(ListSightingsInput.builder().build(), null)
                .getSightings())
                .hasSize(1);

        // Subscribers filter on the subject without decoding the body.
        assertThat(published).hasSize(1);
        assertThat(published.get(0).subject()).isEqualTo("SightingReported");
        assertThat(decode(SightingReported.builder()).getLocation().getLatitude()).isEqualTo(47.6062);
    }

    @Test
    void withdrawingASightingDeletesItAndPublishesAnEvent() {
        String id = report();
        published.clear();

        handlers.withdrawSighting()
                .withdrawSighting(WithdrawSightingInput.builder().sightingId(id).build(), null);

        assertThat(decode(SightingWithdrawn.builder()).getSightingId()).isEqualTo(id);

        // Gone. Subscribers learn about it from the event.
        assertThatThrownBy(() -> handlers.getSighting()
                .getSighting(GetSightingInput.builder().sightingId(id).build(), null))
                .isInstanceOf(SightingNotFound.class);
    }

    private <T extends SerializableStruct> T decode(ShapeBuilder<T> builder) {
        return CODEC.deserializeShape(
                Base64.getDecoder().decode(published.get(0).message()), builder);
    }
}
