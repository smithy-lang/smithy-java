/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import software.amazon.smithy.java.auth.api.AuthProperties;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.client.core.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.utils.IoUtils;

public class SigV4TestRunner {
    private static final String DEFAULT_TEST_CASE_LOCATION = "signing";
    private static final String REQUEST = "request.txt";
    private static final String SIGNED = "signed.txt";
    private static final String CONTEXT = "context.json";

    private final List<TestCase> testCases = new ArrayList<>();

    private SigV4TestRunner() {}

    /**
     * Creates a new SigV4 test suite.
     *
     * @return Returns the created test suite.
     */
    public static SigV4TestRunner runner() {
        return new SigV4TestRunner();
    }

    public static Stream<Object[]> defaultParameterizedTestSource(Class<?> contextClass) {
        return SigV4TestRunner.runner()
                .addTestCasesFromUrl(Objects.requireNonNull(contextClass.getResource(DEFAULT_TEST_CASE_LOCATION)))
                .parameterizedTestSource();
    }

    public SigV4TestRunner addTestCasesFromUrl(URL url) {
        if (!url.getProtocol().equals("file")) {
            throw new IllegalArgumentException("Only file URLs are supported by the testrunner: " + url);
        }

        try {
            return addTestCasesFromDirectory(Paths.get(url.toURI()));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public Stream<Object[]> parameterizedTestSource() {
        return testCases.stream().map(testCase -> {
            Callable<Result> callable = () -> testCase.createResult(SigV4Signer.create());
            Callable<Result> wrappedCallable = () -> callable.call().unwrap();
            return new Object[] {testCase.name(), wrappedCallable};
        });
    }

    public SigV4TestRunner addTestCasesFromDirectory(Path directory) {
        for (var dir : Objects.requireNonNull(directory.toFile().listFiles())) {
            if (dir.isDirectory()) {
                testCases.add(TestCase.from(dir.toPath()));
            }
        }
        return this;
    }

    private record TestCase(String name, Context context, HttpRequest request, HttpRequest expected) {

        public static TestCase from(Path directory) {
            var name = directory.getFileName().toString();
            var context = Context.load(directory);
            var request = loadRequest(directory);
            var signed = loadSigned(directory);
            return new TestCase(name, context, request, signed);

        }

        private static HttpRequest loadRequest(Path directory) {
            String fileName = directory.resolve(REQUEST).toString();
            return parseRequest(fileName);
        }

        private static HttpRequest loadSigned(Path directory) {
            String fileName = directory.resolve(SIGNED).toString();
            return parseRequest(fileName);
        }

        private static HttpRequest parseRequest(String fileName) {
            var fileContents = IoUtils.readUtf8File(fileName);
            var fileLines = new ArrayList<>(fileContents.lines().toList());
            // Parse http header line
            var headerLine = fileLines.remove(0).split(" ");
            var method = headerLine[0];
            var end = headerLine.length - 1;
            var path = String.join("%20", Arrays.copyOfRange(headerLine, 1, end));
            if (!headerLine[end].equals("HTTP/1.1")) {
                throw new UnsupportedOperationException("Unsupported HTTP version " + headerLine[end]);
            }

            // Now read all headers in the request
            String hostValue = null;
            boolean inBody = false;
            StringBuilder body = null;
            Map<String, List<String>> headers = new HashMap<>();
            for (var line : fileLines) {
                if (line.isEmpty()) {
                    // Empty line indicates next line will be body
                    inBody = true;
                    continue;
                }
                if (!inBody) {
                    var splitLine = line.split(":");
                    if (splitLine[0].equalsIgnoreCase("host")) {
                        hostValue = splitLine[1];
                    }
                    headers.put(splitLine[0], List.of(splitLine[1]));
                    // if the line is empty then the next part is the body
                } else {
                    if (body == null) {
                        body = new StringBuilder(line);
                    } else {
                        body.append("\n").append(line);
                    }
                }
            }

            return HttpRequest.builder()
                    .method(method)
                    .httpVersion(HttpVersion.HTTP_1_1)
                    .uri(URI.create("http://" + Objects.requireNonNull(hostValue) + path))
                    .headers(HttpHeaders.of(headers))
                    .body(body != null ? DataStream.ofBytes(body.toString().getBytes()) : null)
                    .build();
        }

        Result createResult(
                Signer<HttpRequest, AwsCredentialsIdentity> signer
        ) throws ExecutionException, InterruptedException {
            var signedRequest = signer.sign(request, context.identity, context.properties).get();
            boolean isValid = signedRequest.headers().equals(expected.headers())
                    && signedRequest.uri().equals(expected.uri())
                    && signedRequest.method().equals(expected.method());
            return new Result(signedRequest, expected, isValid);
        }
    }

    record Context(
            AwsCredentialsIdentity identity,
            AuthProperties properties) {
        static Context load(Path directory) {
            String fileName = directory.resolve(CONTEXT).toString();
            var node = Node.parse(IoUtils.readUtf8File(fileName)).expectObjectNode();
            return new Context(
                    getIdentity(node.expectObjectMember("credentials")),
                    getAuthProperties(node.expectObjectMember("properties")));
        }

        private static AwsCredentialsIdentity getIdentity(ObjectNode credentialsNode) {
            return AwsCredentialsIdentity.create(
                    credentialsNode.expectStringMember("access_key_id").getValue(),
                    credentialsNode.expectStringMember("secret_access_key").getValue(),
                    credentialsNode.getStringMemberOrDefault("token", null));
        }

        private static AuthProperties getAuthProperties(ObjectNode objectNode) {
            return AuthProperties.builder()
                    .put(SigV4Settings.SIGNING_NAME, objectNode.expectStringMember("service").getValue())
                    .put(SigV4Settings.REGION, objectNode.expectStringMember("region").getValue())
                    .put(SigV4Settings.CLOCK, getStaticClock(objectNode.expectStringMember("timestamp").getValue()))
                    .build();
        }

        private static Clock getStaticClock(String timeString) {
            return Clock.fixed(Instant.parse(timeString), ZoneId.of("UTC"));
        }
    }

    public record Result(HttpRequest actual, HttpRequest expected, boolean isValid) {
        public Result unwrap() {
            if (!isValid()) {
                throw new AssertionError(
                        "Expected does not match actual. \n"
                                + "Expected: -------------- \n" + expected()
                                + "\n  Actual -------------- \n" + actual());
            }
            return this;
        }
    }
}
