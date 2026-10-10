/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.benchmarks.e2e.generated.awsjson10.client.AwsJson10Client;
import software.amazon.smithy.java.benchmarks.e2e.generated.awsquery.client.AwsQueryClient;
import software.amazon.smithy.java.benchmarks.e2e.generated.restjson.client.RestJson1Client;
import software.amazon.smithy.java.benchmarks.e2e.generated.restxml.client.RestXmlClient;
import software.amazon.smithy.java.benchmarks.e2e.generated.rpcv2cbor.client.RpcV2CborClient;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.context.Context;

enum BenchmarkProtocol {
    AWS_JSON_1_0(
            "awsJson1_0",
            "AwsJson10",
            "awsjson10",
            "application/x-amz-json-1.0",
            bytes("{}"),
            false),
    RPC_V2_CBOR(
            "rpcv2Cbor",
            "RpcV2Cbor",
            "rpcv2cbor",
            "application/cbor",
            new byte[] {(byte) 0xa0},
            true),
    AWS_QUERY(
            "awsQuery",
            "AwsQuery",
            "awsquery",
            "text/xml",
            new byte[0],
            false),
    REST_JSON_1(
            "restJson1",
            "RestJson1",
            "restjson",
            "application/json",
            bytes("{}"),
            false),
    REST_XML(
            "restXml",
            "RestXml",
            "restxml",
            "application/xml",
            new byte[0],
            false);

    static final String ENDPOINT = "https://example.com";
    static final String REGION = "us-east-1";

    private static final String GENERATED_ROOT = "software.amazon.smithy.java.benchmarks.e2e.generated.";
    private static final AwsCredentialsIdentity CREDENTIALS = AwsCredentialsIdentity.create(
            "AKIDEXAMPLE",
            "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY");

    /**
     * Register credentials under AwsCredentialsIdentity so SigV4 finds them.
     * Concrete resolver types do not match the auth interface, so the client would use host credentials.
     */
    private static final IdentityResolver<AwsCredentialsIdentity> STATIC_CREDENTIALS = new IdentityResolver<>() {
        private final IdentityResult<AwsCredentialsIdentity> result = IdentityResult.of(CREDENTIALS);

        @Override
        public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
            return result;
        }

        @Override
        public Class<AwsCredentialsIdentity> identityType() {
            return AwsCredentialsIdentity.class;
        }
    };

    private final String idPrefix;
    private final String summaryName;
    private final String generatedPackage;
    private final String contentType;
    private final byte[] emptyResponseBody;
    private final boolean base64Bodies;

    BenchmarkProtocol(
            String idPrefix,
            String summaryName,
            String packageSuffix,
            String contentType,
            byte[] emptyResponseBody,
            boolean base64Bodies
    ) {
        this.idPrefix = idPrefix;
        this.summaryName = summaryName;
        this.generatedPackage = GENERATED_ROOT + packageSuffix;
        this.contentType = contentType;
        this.emptyResponseBody = emptyResponseBody;
        this.base64Bodies = base64Bodies;
    }

    String idPrefix() {
        return idPrefix;
    }

    String summaryName() {
        return summaryName;
    }

    String generatedPackage() {
        return generatedPackage;
    }

    String contentType() {
        return contentType;
    }

    byte[] minimalResponseBody(String operationName) {
        if (this == AWS_QUERY) {
            return bytes("<" + operationName + "Response><" + operationName + "Result/></" + operationName
                    + "Response>");
        }
        return emptyResponseBody.clone();
    }

    byte[] emptyResponseBody() {
        return emptyResponseBody.clone();
    }

    boolean base64Bodies() {
        return base64Bodies;
    }

    Object newClient(ClientTransport<?, ?> transport, String endpoint) {
        return switch (this) {
            case AWS_JSON_1_0 -> configure(AwsJson10Client.builder(), transport, endpoint).build();
            case RPC_V2_CBOR -> configure(RpcV2CborClient.builder(), transport, endpoint).build();
            case AWS_QUERY -> configure(AwsQueryClient.builder(), transport, endpoint).build();
            case REST_JSON_1 -> configure(RestJson1Client.builder(), transport, endpoint).build();
            case REST_XML -> configure(RestXmlClient.builder(), transport, endpoint).build();
        };
    }

    Class<?> clientInterface() {
        return switch (this) {
            case AWS_JSON_1_0 -> AwsJson10Client.class;
            case RPC_V2_CBOR -> RpcV2CborClient.class;
            case AWS_QUERY -> AwsQueryClient.class;
            case REST_JSON_1 -> RestJson1Client.class;
            case REST_XML -> RestXmlClient.class;
        };
    }

    static BenchmarkProtocol forBenchmarkId(String id) {
        for (var protocol : values()) {
            if (id.startsWith(protocol.idPrefix + "_")) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("No protocol matches benchmark id '" + id + "'");
    }

    static BenchmarkProtocol parse(String name) {
        for (var protocol : values()) {
            if (protocol.idPrefix.equalsIgnoreCase(name)
                    || protocol.summaryName.equalsIgnoreCase(name)
                    || protocol.name().equalsIgnoreCase(name)) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("Unknown protocol '" + name + "'. Expected one of: " + names());
    }

    static String names() {
        var sb = new StringBuilder();
        for (var protocol : values()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(protocol.idPrefix);
        }
        return sb.toString();
    }

    private static <B extends Client.Builder<?, B>> B configure(
            B builder,
            ClientTransport<?, ?> transport,
            String endpoint
    ) {
        return builder.transport(transport)
                .endpoint(endpoint)
                .putConfig(RegionSetting.REGION, REGION)
                .addIdentityResolver(STATIC_CREDENTIALS);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
