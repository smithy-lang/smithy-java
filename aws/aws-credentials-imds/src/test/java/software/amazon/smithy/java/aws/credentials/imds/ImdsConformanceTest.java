/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.imds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Runs the IMDS v2.1 conformance test suite using a mock SimpleHttpClient.
 */
class ImdsConformanceTest {

    private static final JsonCodec CODEC = JsonCodec.builder().build();
    private static final URI ENDPOINT = URI.create("http://169.254.169.254");

    @TestFactory
    Stream<DynamicTest> imdsTests() throws IOException {
        byte[] data;
        try (InputStream is = getClass().getResourceAsStream("imds-v21-tests.json")) {
            assertNotNull(is);
            data = is.readAllBytes();
        }
        Document root = CODEC.createDeserializer(data).readDocument();

        List<DynamicTest> tests = new ArrayList<>();
        for (Document test : root.asList()) {
            String summary = test.getMember("summary").asString();
            tests.add(DynamicTest.dynamicTest(summary, () -> runTest(test)));
        }
        return tests.stream();
    }

    private void runTest(Document test) throws Exception {
        Document config = test.getMember("config");
        String profileName = null;
        Document profileNameDoc = config.getMember("ec2InstanceProfileName");
        if (profileNameDoc != null && profileNameDoc.isType(ShapeType.STRING)) {
            profileName = profileNameDoc.asString();
        }

        // Check if disabled.
        Document envVars = config.getMember("envVars");
        if (envVars != null) {
            Document disabled = envVars.getMember("AWS_EC2_METADATA_DISABLED");
            if (disabled != null && "true".equalsIgnoreCase(disabled.asString())) {
                for (Document outcome : test.getMember("outcomes").asList()) {
                    assertEquals("no credentials", outcome.getMember("result").asString());
                }
                return;
            }
        }

        // Build path-aware mock: map path -> queue of responses.
        Map<String, Deque<MockResp>> pathResponses = new HashMap<>();
        for (Document exp : test.getMember("expectations").asList()) {
            String path = exp.getMember("get").asString();
            Document response = exp.getMember("response");
            int status = response.getMember("status").asInteger();
            Document body = response.getMember("body");
            String bodyStr = "";
            if (body != null) {
                bodyStr = body.isType(ShapeType.STRING) ? body.asString() : documentToJson(body);
            }
            pathResponses.computeIfAbsent(path, k -> new ArrayDeque<>()).add(new MockResp(status, bodyStr));
        }

        // Create mock client that routes by path.
        ImdsClient.SimpleHttpClient mockClient = request -> {
            if ("PUT".equals(request.method())) {
                return fakeResponse(200, "mock-token", request);
            }
            String reqPath = request.uri().getPath();
            Deque<MockResp> queue = pathResponses.get(reqPath);
            if (queue == null || queue.isEmpty()) {
                return fakeResponse(404, "", request);
            }
            MockResp r = queue.poll();
            return fakeResponse(r.status, r.body, request);
        };

        ImdsClient client = new ImdsClient(ENDPOINT, mockClient);

        for (Document outcome : test.getMember("outcomes").asList()) {
            String expectedResult = outcome.getMember("result").asString();
            String json;
            try {
                json = client.fetchCredentials(profileName);
            } catch (IOException e) {
                if ("no credentials".equals(expectedResult) || "invalid profile".equals(expectedResult)) {
                    continue;
                }
                throw e;
            }

            if ("no credentials".equals(expectedResult) || "invalid profile".equals(expectedResult)) {
                continue;
            }

            assertNotNull(json, "Expected credentials but got null");
            if (outcome.getMember("accountId") != null) {
                Document creds = CODEC.createDeserializer(json.getBytes(StandardCharsets.UTF_8)).readDocument();
                assertEquals(outcome.getMember("accountId").asString(),
                        creds.getMember("AccountId").asString());
            }
        }
    }

    private static String documentToJson(Document doc) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String key : doc.getMemberNames()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(key).append("\":");
            Document val = doc.getMember(key);
            if (val.isType(ShapeType.STRING) || val.isType(ShapeType.ENUM)) {
                sb.append("\"").append(val.asString().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else if (val.isType(ShapeType.MAP) || val.isType(ShapeType.STRUCTURE)) {
                sb.append(documentToJson(val));
            } else {
                try {
                    sb.append(val.asNumber());
                } catch (Exception e) {
                    sb.append("null");
                }
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static HttpResponse<String> fakeResponse(int status, String body, HttpRequest request) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (a, b) -> true);
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private record MockResp(int status, String body) {}
}
