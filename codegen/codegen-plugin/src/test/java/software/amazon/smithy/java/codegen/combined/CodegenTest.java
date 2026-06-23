/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.combined;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.java.codegen.JavaCodegenPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Verifies combined TYPES + CLIENT generation: the primary service is generated as a client
 * and an unconnected standalone type (which a plain service walk would not reach) is also
 * generated as a POJO from the same run.
 */
public class CodegenTest {
    private static final URL testFile =
            Objects.requireNonNull(CodegenTest.class.getResource("combined-it.smithy"));
    private static final Model model = Model.assembler()
            .addImport(testFile)
            .assemble()
            .unwrap();

    @Test
    void generatesServiceAndStandaloneTypes() {
        var manifest = new MockManifest();
        SmithyBuildPlugin plugin = new JavaCodegenPlugin();
        var settings = ObjectNode.builder()
                .withMember("service", "smithy.java.codegen.combined.it#CombinedItService")
                .withMember("namespace", "test.smithy.codegen")
                .withMember("modes", ArrayNode.fromStrings("client", "types"))
                .build();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .settings(settings)
                .model(model)
                .build();

        plugin.execute(context);

        assertThat(manifest.getFiles())
                // Client artifact for the primary service.
                .contains(Path.of("/java/test/smithy/codegen/client/CombinedItServiceClient.java"))
                // Type connected to the service through an operation, generated under its renamed name.
                .contains(Path.of("/java/test/smithy/codegen/model/Gadget.java"))
                // Standalone type not reachable from the service is still generated.
                .contains(Path.of("/java/test/smithy/codegen/model/StandaloneType.java"));
    }

    @Test
    void generatesFromAuthoredClosureInCombinedMode() {
        // The model authors a `shapeClosures` entry that includes the service and the standalone type.
        // Referencing it by id drives combined generation; the director enforces the service is a member.
        var manifest = new MockManifest();
        SmithyBuildPlugin plugin = new JavaCodegenPlugin();
        var settings = ObjectNode.builder()
                .withMember("service", "smithy.java.codegen.combined.it#CombinedItService")
                .withMember("namespace", "test.smithy.codegen")
                .withMember("modes", ArrayNode.fromStrings("client", "types"))
                .withMember("closure", "smithy.java.codegen.combined.it#combinedClosure")
                .build();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .settings(settings)
                .model(model)
                .build();

        plugin.execute(context);

        assertThat(manifest.getFiles())
                // The service still generates as a client, and the standalone type is included.
                .contains(Path.of("/java/test/smithy/codegen/client/CombinedItServiceClient.java"))
                .contains(Path.of("/java/test/smithy/codegen/model/Gadget.java"))
                .contains(Path.of("/java/test/smithy/codegen/model/StandaloneType.java"));
    }

    @Test
    void skipsShapesNotBoundToPrimaryService() {
        // The authored closure pulls in an operation and a resource that are not bound to the
        // primary service. Combined mode must skip both artifacts while still generating the
        // primary service.
        var manifest = new MockManifest();
        SmithyBuildPlugin plugin = new JavaCodegenPlugin();
        var settings = ObjectNode.builder()
                .withMember("service", "smithy.java.codegen.combined.it#CombinedItService")
                .withMember("namespace", "test.smithy.codegen")
                .withMember("modes", ArrayNode.fromStrings("client", "types"))
                .withMember("closure", "smithy.java.codegen.combined.it#closureWithUnboundShapes")
                .build();
        var context = PluginContext.builder()
                .fileManifest(manifest)
                .settings(settings)
                .model(model)
                .build();

        plugin.execute(context);

        assertThat(manifest.getFiles())
                // The primary service still generates.
                .contains(Path.of("/java/test/smithy/codegen/client/CombinedItServiceClient.java"))
                // No operation or resource artifact is generated for the shapes outside the primary
                // service's closure. (OrphanOpInput below proves the operation did enter the closure,
                // so the missing OrphanOp artifact reflects the skip, not an empty closure.)
                .doesNotContain(Path.of("/java/test/smithy/codegen/model/OrphanOp.java"))
                .doesNotContain(Path.of("/java/test/smithy/codegen/model/OrphanResource.java"))
                // The unbound operation's input and output are still generated as standalone data
                // shapes, which is expected: they are plain data, unlike the operation artifact
                // whose service wiring would be wrong.
                .contains(Path.of("/java/test/smithy/codegen/model/OrphanOpInput.java"))
                .contains(Path.of("/java/test/smithy/codegen/model/OrphanOpOutput.java"));
    }
}
