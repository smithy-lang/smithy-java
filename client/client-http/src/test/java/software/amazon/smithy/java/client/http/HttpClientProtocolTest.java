/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.model.shapes.ShapeId;

public class HttpClientProtocolTest {
    @Test
    public void mergesPaths() throws Exception {
        var hcp = new HttpClientProtocol(ShapeId.from("foo#Bar")) {
            @Override
            public Codec payloadCodec() {
                return null;
            }

            @Override
            public <I extends SerializableStruct, O extends SerializableStruct> HttpRequest createRequest(
                    ApiOperation<I, O> operation,
                    I input,
                    Context context,
                    URI endpoint
            ) {
                return null;
            }

            @Override
            public <I extends SerializableStruct,
                    O extends SerializableStruct> O deserializeResponse(
                            ApiOperation<I, O> operation,
                            Context context,
                            TypeRegistry errorRegistry,
                            HttpRequest request,
                            HttpResponse response
                    ) {
                return null;
            }
        };

        var endpoint = Endpoint.builder().uri("https://example.com/foo%20/bar").build();
        var request = HttpRequest.builder()
                .method("GET")
                .uri(new URI("/bam%20"))
                .build();
        var merged = hcp.setServiceEndpoint(request, endpoint);

        // It concats the endpoints and maintains percent encoding.
        assertThat(merged.uri().toString(), equalTo("https://example.com/foo%20/bar/bam%20"));
    }
}
