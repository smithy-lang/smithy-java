/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoint.api;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public class EndpointResolverTest {
    @Test
    public void returnsStaticEndpoint() {
        EndpointResolver resolver = EndpointResolver
            .staticEndpoint(Endpoint.builder().uri("https://example.com").build());

        var testSchema = Schema.createOperation(ShapeId.from("com.example#Foo"));
        var input = new SerializableStruct() {
            @Override
            public void serialize(ShapeSerializer encoder) {
                // do nothing for test.
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                // do nothing for test.
            }
        };
        Endpoint endpoint = resolver.resolveEndpoint(
            EndpointResolverParams.builder()
                .operationSchema(testSchema)
                .inputShape(input)
                .build()
        ).join();

        MatcherAssert.assertThat(
            endpoint.uri().toString(),
            Matchers.equalTo("https://example.com")
        );
    }
}
