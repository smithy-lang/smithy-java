/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.example.closure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.example.closure.model.Classification;
import software.amazon.smithy.java.example.closure.model.Coordinates;
import software.amazon.smithy.java.example.closure.model.VerifiedSighting;
import software.amazon.smithy.java.example.closure.service.IBird;
import software.amazon.smithy.java.server.Service;

/**
 * Verifies combined-mode generation driven by the {@code fullService} shape closure: the bird
 * service is generated as a server (see {@code IBird} under the {@code service} package) and the
 * service closure's data shapes, including the event types, are generated alongside it.
 */
public class CombinedGenerationTest {

    @Test
    void generatesServerForService() {
        // The service is generated as a server Service implementation.
        assertThat(Service.class).isAssignableFrom(IBird.class);
    }

    @Test
    void generatesServiceClosureTypes() {
        // Classification is part of the service closure and generates as a normal data shape.
        var classification = Classification.builder()
                .order("Passeriformes")
                .family("Corvidae")
                .genus("Corvus")
                .species("corax")
                .build();
        assertThat(classification.getGenus()).isEqualTo("Corvus");
        assertThat(classification.getSpecies()).isEqualTo("corax");
    }

    @Test
    void generatesEventTypesFromClosure() {
        // VerifiedSighting is a public event tagged "event"; the closure pulls it into generation
        // alongside the server. All of its members are required, so populate them all.
        var sighting = VerifiedSighting.builder()
                .birdId("bird-1")
                .sightingId("sighting-1")
                .timestamp(Instant.EPOCH)
                .location(Coordinates.builder().build())
                .verified(true)
                .classification(Classification.builder()
                        .order("Passeriformes")
                        .family("Corvidae")
                        .genus("Corvus")
                        .species("corax")
                        .build())
                .build();
        assertThat(sighting.getBirdId()).isEqualTo("bird-1");
        assertThat(sighting.isVerified()).isTrue();
    }
}
