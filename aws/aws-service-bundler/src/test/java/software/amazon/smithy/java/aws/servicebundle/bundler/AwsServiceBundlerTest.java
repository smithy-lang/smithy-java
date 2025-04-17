/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.bundler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.InputStreamReader;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;

public class AwsServiceBundlerTest {
    @Test
    public void accessAnalyzer() {
        var bundler = new AwsServiceBundler("accessanalyzer-2019-11-01.json", AwsServiceBundlerTest::getModel);
        var bundle = bundler.bundle().getConfig().asShape(AwsServiceMetadata.builder());

        assertEquals("access-analyzer", bundle.getSigv4SigningName());
        assertEquals("AccessAnalyzer", bundle.getServiceName());

        assertNotEquals(0, bundle.getEndpoints().size());
    }

    private static String getModel(String path) {
        try (var stream = new InputStreamReader(Objects
                .requireNonNull(AwsServiceBundlerTest.class.getResourceAsStream(path), "No model named " + path))) {
            var builder = new StringBuilder();
            var buffer = new char[1024];
            while (true) {
                var read = stream.read(buffer);
                if (read == -1) {
                    break;
                }
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
