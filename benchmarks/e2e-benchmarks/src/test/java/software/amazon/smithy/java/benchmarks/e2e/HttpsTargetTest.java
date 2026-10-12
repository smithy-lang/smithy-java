/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.http.boringssl.BoringSslTlsProvider;
import software.amazon.smithy.java.core.schema.Schema;

/** The whole https path: child-JVM fixture server, control port, BoringSSL on both ends, fixture switching. */
class HttpsTargetTest {

    @Test
    void servesSuccessiveCasesOverTlsFromAChildJvm() throws Throwable {
        assumeTrue(BoringSslTlsProvider.available(), "netty-tcnative (BoringSSL) is not available on this host");
        var getObject = BenchmarkCases.build("restXml_GetObject_M");
        var putObject = BenchmarkCases.build("restXml_PutObject_S");
        var getItem = BenchmarkCases.build("awsJson1_0_GetItemOutput_S");

        try (Target target = Target.open(Mode.HTTPS)) {
            assertThat(target.endpoint()).startsWith("https://127.0.0.1:");
            var restXml = new BenchmarkClient(getObject.protocol(), target.transport(), target.endpoint());
            var awsJson = new BenchmarkClient(getItem.protocol(), target.transport(), target.endpoint());
            try {
                target.respondWith(getObject.response());
                var call = restXml.prepare(getObject);
                var output = call.invoke();
                Schema body = getObject.operation().outputSchema().member("Body");
                assertThat((Object) output.getMemberValue(body)).as("streamed body deserialized").isNotNull();
                for (int i = 0; i < 5; i++) {
                    call.invoke();
                }

                // Switching the fixture must take effect on the kept-alive connection.
                target.respondWith(putObject.response());
                assertThat(restXml.prepare(putObject).invoke()).isNotNull();

                target.respondWith(getItem.response());
                var item = awsJson.prepare(getItem).invoke();
                Schema itemMember = getItem.operation().outputSchema().member("Item");
                assertThat((Object) item.getMemberValue(itemMember)).isNotNull();
            } finally {
                restXml.close();
                awsJson.close();
            }
        }
    }
}
