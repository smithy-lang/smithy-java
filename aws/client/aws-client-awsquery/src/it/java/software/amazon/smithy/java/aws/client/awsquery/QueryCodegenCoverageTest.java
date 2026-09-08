/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecRegistry;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenStats;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Asserts the generated path actually covers the protocol-test models.
 *
 * <p>The compliance suites cannot show this on their own. An unsupported shape falls back to the
 * interpreted serializer silently and by design, so a green {@code integ-codegen} run is equally
 * consistent with every shape having been generated and with none of them having been. This walks the
 * two protocol-test models directly and reports, by name, anything the backend declined.
 */
public class QueryCodegenCoverageTest {

    @Test
    public void awsQueryGeneratesForEveryProtocolTestShape() {
        assertGeneratesEverything(
                QueryFormSerializer.QueryVariant.AWS_QUERY,
                "aws.protocoltests.query",
                List.of());
    }

    /**
     * EC2 Query has no wire representation for a map, so a shape carrying one is declined rather than
     * mis-serialized. The two shapes below are the only ones in the EC2 test model that do; both are
     * operation outputs, which this protocol never serializes, so nothing on the request path is left
     * to the interpreted serializer. The set is pinned rather than computed so that a shape becoming
     * unsupported for some other reason shows up here as a failure.
     */
    @Test
    public void ec2QueryGeneratesForEveryProtocolTestShape() {
        assertGeneratesEverything(
                QueryFormSerializer.QueryVariant.EC2_QUERY,
                "aws.protocoltests.ec2",
                List.of("aws.protocoltests.ec2#XmlEnumsOutput", "aws.protocoltests.ec2#XmlIntEnumsOutput"));
    }

    private void assertGeneratesEverything(
            QueryFormSerializer.QueryVariant variant,
            String namespace,
            List<String> expectedDeclined
    ) {
        assumeTrue(RuntimeCodegenFeature.available(), "runtime codegen needs JDK 25");

        // The projections register their index through the ServiceLoader, so this reaches both models
        // without naming their generated classes; the namespace filter keeps the two apart.
        List<Schema> roots = new ArrayList<>();
        SchemaIndex.getCombinedSchemaIndex().visit(schema -> {
            if ((schema.type() == ShapeType.STRUCTURE || schema.type() == ShapeType.UNION)
                    && schema.shapeClass() != null
                    && schema.id().getNamespace().equals(namespace)) {
                roots.add(schema);
            }
        });
        assertTrue(roots.size() > 20, "expected a populated schema index, found " + roots.size());

        // A fresh registry and settings identity, so the counts below are this test's alone and nothing
        // is served from the cache the protocols share.
        RuntimeCodecRegistry<GeneratedQueryCodec> registry =
                new RuntimeCodecRegistry<>(new QueryRuntimeCodegenBackend(variant));
        Object settings = new Object();
        RuntimeCodegenStats.Snapshot before = RuntimeCodegenStats.snapshot("awsquery");

        List<String> declined = new ArrayList<>();
        for (Schema root : roots) {
            if (registry.get(root, settings) == null) {
                declined.add(root.id().toString());
            }
        }

        RuntimeCodegenStats.Snapshot after = RuntimeCodegenStats.snapshot("awsquery");
        declined.sort(null);
        assertEquals(expectedDeclined, declined, "shapes the " + variant + " backend declined");
        assertEquals(roots.size() - expectedDeclined.size(), after.generated() - before.generated());
        // Strict mode turns an emitter bug into a throw, so this is belt and braces for the run that
        // does not set it.
        assertEquals(before.failed(), after.failed(), "runtime codegen reported an emitter failure");
    }
}
