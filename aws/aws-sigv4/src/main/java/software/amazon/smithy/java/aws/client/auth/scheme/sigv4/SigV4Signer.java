/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * AWS signature version 4 signing implementation.
 */
final class SigV4Signer implements Signer<HttpRequest, AwsCredentialsIdentity> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(SigV4Signer.class);
    private static final String ALGORITHM_NAME = "AWS4-HMAC-SHA256";
    private static final byte[] ALGORITHM = ALGORITHM_NAME.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CREDENTIAL_PREFIX =
            (ALGORITHM_NAME + " Credential=").getBytes(StandardCharsets.US_ASCII);
    private static final SigningCache<SigningCache.CacheKey, SigningKey> SIGNER_CACHE = new SigningCache<>(300);

    private final SigningResources r;

    public static SigV4Signer create() {
        return new SigV4Signer();
    }

    private SigV4Signer() {
        this.r = SigningResources.get();
    }

    @Override
    public void close() {
        SigningResources.release(r);
    }

    @Override
    public SignResult<HttpRequest> sign(HttpRequest request, AwsCredentialsIdentity identity, Context properties) {
        var service = SigningParameters.signingName(properties);
        var region = SigningParameters.region(properties);
        boolean isS3 = SigningParameters.isS3(service);

        // TODO: Add support for query signing?

        String payloadHash = HttpSigning.resolvePayloadHash(request, properties, r);
        var mod = request.toModifiable();
        HttpSigning.canonicalize(mod, properties, r, payloadHash, identity.sessionToken(), isS3);

        int stringToSignLength = HttpSigning.stringToSign(r, ALGORITHM, region, service);
        var signingKey = deriveSigningKey(identity.secretAccessKey(), region, service);
        r.hmac(signingKey, r.stringToSignBytes, 0, stringToSignLength, r.signatureBytes);
        int signatureLength = SigningResources.writeHex(r.signatureBytes, r.signatureHex, 0);
        return HttpSigning
                .authorize(mod, r, CREDENTIAL_PREFIX, identity.accessKeyId(), region, service, signatureLength);
    }

    String lastCanonicalRequest() {
        return new String(r.out, 0, r.outLength, StandardCharsets.UTF_8);
    }

    private byte[] deriveSigningKey(String secretKey, String regionName, String serviceName) {
        byte[] memoized = r.memoizedSigningKey(secretKey, regionName, serviceName);
        if (memoized != null) {
            return memoized;
        }
        var cacheKey = new SigningCache.CacheKey(secretKey, regionName, serviceName);
        SigningKey signingKey = SIGNER_CACHE.get(cacheKey);
        byte[] key;
        if (signingKey != null && signingKey.isValidFor(r.cachedDays)) {
            key = signingKey.signingKey();
        } else {
            LOGGER.trace("Generating new key as signing key could not be found in cache.");
            key = newSigningKey(secretKey, regionName, serviceName);
            SIGNER_CACHE.put(cacheKey, new SigningKey(key, r.cachedDays));
        }
        r.memoizeSigningKey(secretKey, regionName, serviceName, key);
        return key;
    }

    private byte[] newSigningKey(String secretKey, String regionName, String serviceName) {
        var kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        var kDate = r.hmac(kSecret, r.dateStamp.getBytes(StandardCharsets.UTF_8));
        var kRegion = r.hmac(kDate, regionName.getBytes(StandardCharsets.UTF_8));
        var kService = r.hmac(kRegion, serviceName.getBytes(StandardCharsets.UTF_8));
        return r.hmac(kService, HttpSigning.TERMINATOR_BYTES);
    }
}
