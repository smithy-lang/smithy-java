/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JavaCodegenSettingsTest {

    @Test
    void serviceLessWithoutNameDefaultsToLegacyName() {
        // Types-only generation has no service to derive a name from. Rather than failing, it falls
        // back to the legacy default (and logs a warning) so existing nameless configs keep working.
        var settings = JavaCodegenSettings.builder()
                .packageNamespace("com.example.standalone")
                .build();
        assertThat(settings.getService()).isEmpty();
        assertThat(settings.name()).isEqualTo("TypesGenService");
    }

    @Test
    void serviceLessWithExplicitNameSucceeds() {
        var settings = JavaCodegenSettings.builder()
                .packageNamespace("com.example.standalone")
                .name("standalone")
                .build();
        assertThat(settings.getService()).isEmpty();
        assertThat(settings.name()).isEqualTo("Standalone");
    }

    @Test
    void withServiceDerivesNameFromService() {
        var settings = JavaCodegenSettings.builder()
                .packageNamespace("com.example")
                .service("com.example#MyService")
                .build();
        assertThat(settings.name()).isEqualTo("MyService");
    }

    @Test
    void explicitNameTakesPrecedenceOverService() {
        var settings = JavaCodegenSettings.builder()
                .packageNamespace("com.example")
                .service("com.example#MyService")
                .name("override")
                .build();
        assertThat(settings.name()).isEqualTo("Override");
    }
}
