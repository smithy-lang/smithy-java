/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;

final class SigV4aSigner implements Signer<HttpRequest, AwsCredentialsIdentity> {

    static final String ALGORITHM_NAME = "AWS4-ECDSA-P256-SHA256";
    static final HeaderName X_AMZ_REGION_SET = HeaderName.of("x-amz-region-set");

    private static final byte[] ALGORITHM = ALGORITHM_NAME.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CREDENTIAL_PREFIX =
            (ALGORITHM_NAME + " Credential=").getBytes(StandardCharsets.US_ASCII);

    private final SigningResources r;

    static SigV4aSigner create() {
        return new SigV4aSigner();
    }

    private SigV4aSigner() {
        this.r = SigningResources.get();
    }

    @Override
    public void close() {
        SigningResources.release(r);
    }

    @Override
    public SignResult<HttpRequest> sign(HttpRequest request, AwsCredentialsIdentity identity, Context properties) {
        var service = SigningParameters.signingName(properties);
        var regionSet = SigningParameters.regionSet(properties, r);
        boolean isS3 = SigningParameters.isS3(service);

        String payloadHash = HttpSigning.resolvePayloadHash(request, properties, r);
        var mod = request.toModifiable();
        // Set before canonicalization so the signature covers the region set sent on the wire.
        mod.headers().setHeader(X_AMZ_REGION_SET, regionSet);
        HttpSigning.canonicalize(mod, properties, r, payloadHash, identity.sessionToken(), isS3);

        int stringToSignLength = HttpSigning.stringToSign(r, ALGORITHM, null, service);
        ECPrivateKey signingKey = SigV4aKeyDerivation.derive(identity.accessKeyId(), identity.secretAccessKey(), r);
        byte[] der = r.ecdsaSign(signingKey, r.stringToSignBytes, 0, stringToSignLength);
        if (der.length * 2 > r.signatureHex.length) {
            throw new IllegalStateException("Unexpected ECDSA signature length " + der.length);
        }
        int signatureLength = SigningResources.writeHex(der, r.signatureHex, 0);
        return HttpSigning.authorize(mod, r, CREDENTIAL_PREFIX, identity.accessKeyId(), null, service, signatureLength);
    }

    static String joinRegionSet(List<String> regions) {
        return String.join(",", normalizeRegionSet(regions));
    }

    static List<String> normalizeRegionSet(List<String> regions) {
        if (regions == null || regions.isEmpty()) {
            throw new IllegalArgumentException("signingRegionSet must contain at least one region");
        }
        List<String> result = new ArrayList<>(regions.size());
        for (String region : regions) {
            if (region == null) {
                throw new IllegalArgumentException("signingRegionSet must not contain null entries");
            }
            String trimmed = region.trim();
            if (trimmed.isEmpty() || result.contains(trimmed)) {
                continue;
            }
            if (trimmed.indexOf(',') >= 0 || hasWhitespace(trimmed)) {
                throw new IllegalArgumentException("Invalid region in signingRegionSet: '" + region + "'");
            }
            result.add(trimmed);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("signingRegionSet must contain at least one non-blank region");
        }
        return List.copyOf(result);
    }

    private static boolean hasWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    String lastCanonicalRequest() {
        return new String(r.out, 0, r.outLength, StandardCharsets.UTF_8);
    }

    String lastStringToSign() {
        return new String(r.stringToSignBytes, 0, r.stringToSignLength, StandardCharsets.ISO_8859_1);
    }
}
