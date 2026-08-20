/*
 * Example license header.
 * File header line two
 */

package com.example.audubon.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.audubon.client.client.BirdWatcherClient;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * A client generated from the service alone, with no {@code closure} setting and no
 * {@code types} mode, so it gets the operation shapes and nothing else.
 */
class ClientGenerationTest {

    @Test
    void generatesAMethodPerOperation() {
        assertThat(BirdWatcherClient.class.getMethods())
                .extracting(Method::getName)
                .contains("reportSighting", "getSighting", "listSightings", "withdrawSighting");
    }

    @Test
    void generatesNoEventTypes() {
        // No operation reaches the events and no closure is set, so they are absent.
        assertThatThrownBy(() -> Class.forName("com.example.audubon.client.model.SightingReported"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
