/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LambdaEndpointTest {

    private static final LambdaMain handler = new LambdaMain();

    @Test
    public void canAddBeer() {
        String body = """
            {
                "beer": {
                    "name": "Oatmeal Stout",
                    "quanity": 1
                }
            }
            """;

        ProxyRequest request = ProxyRequest.builder()
            .httpMethod("POST")
            .path("/add-beer")
            .requestContext(
                RequestContext.builder()
                    .requestId("abc123")
                    .build()
            )
            .body(body)
            .build();

        ProxyResponse response = handler.handleRequest(request, null);
        String expectedBody = "{\"id\":2}";

        assertEquals(200, response.getStatusCode());
        assertEquals(expectedBody, response.getBody());
    }

    @Test
    public void canGetBeer() {
        String body = """
            {
                "id": 1
            }
            """;

        ProxyRequest request = ProxyRequest.builder()
            .httpMethod("POST")
            .path("/get-beer")
            .requestContext(
                RequestContext.builder()
                    .requestId("abc123")
                    .build()
            )
            .body(body)
            .build();

        ProxyResponse response = handler.handleRequest(request, null);
        String expectedBody = "{\"beer\":{\"name\":\"Munich Helles\",\"quantity\":1}}";

        assertEquals(200, response.getStatusCode());
        assertEquals(expectedBody, response.getBody());
    }
}
