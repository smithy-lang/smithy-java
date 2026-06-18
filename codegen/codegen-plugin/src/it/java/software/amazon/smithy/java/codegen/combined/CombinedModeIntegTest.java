/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.combined;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import smithy.java.codegen.combined.it.client.CombinedItServiceClient;
import smithy.java.codegen.combined.it.model.Gadget;
import smithy.java.codegen.combined.it.model.StandaloneType;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Verifies combined {@code [client, types]} generation produces code that actually compiles and
 * runs. The mere ability to import these classes proves the generated sources compiled:
 * <ul>
 *     <li>{@link CombinedItServiceClient} - the service was generated as a client.</li>
 *     <li>{@code Gadget} - the {@code Widget} shape was renamed via the service {@code rename}
 *         block, so the rename threaded all the way through to the generated class name (there is
 *         no {@code Widget} class to import).</li>
 *     <li>{@link StandaloneType} - an unconnected type a plain service walk would not reach was
 *         still generated.</li>
 * </ul>
 */
public class CombinedModeIntegTest {

    @Test
    void renamedTypeRoundTrips() {
        var gadget = Gadget.builder().name("a").build();
        var output = Gadget.builder();
        Document.of(gadget).deserializeInto(output);
        assertEquals(gadget, output.build());
    }

    @Test
    void standaloneTypeRoundTrips() {
        var standalone = StandaloneType.builder().value("v").build();
        var output = StandaloneType.builder();
        Document.of(standalone).deserializeInto(output);
        assertEquals(standalone, output.build());
    }

    @Test
    void clientInterfaceIsGenerated() {
        // Referencing the generated client type confirms combined mode emitted compilable client
        // code alongside the data shapes.
        assertEquals("CombinedItServiceClient", CombinedItServiceClient.class.getSimpleName());
    }
}
