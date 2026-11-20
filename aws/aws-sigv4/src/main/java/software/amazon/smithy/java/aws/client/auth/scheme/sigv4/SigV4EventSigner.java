/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;

final class SigV4EventSigner extends AwsEventSigner {

    SigV4EventSigner(AwsCredentialsIdentity identity, Context signerContext, String seedSignature) {
        super("AWS4-HMAC-SHA256-PAYLOAD", identity, signerContext, seedSignature);
    }

    @Override
    String scopeRegion() {
        return SigningParameters.region(signerContext);
    }

    @Override
    byte[] sign(byte[] stringToSign, String region, String service, SigningResources resources) {
        var kSecret = ("AWS4" + identity.secretAccessKey()).getBytes(StandardCharsets.UTF_8);
        var kDate = resources.hmac(kSecret, resources.dateStamp.getBytes(StandardCharsets.UTF_8));
        var kRegion = resources.hmac(kDate, region.getBytes(StandardCharsets.UTF_8));
        var kService = resources.hmac(kRegion, service.getBytes(StandardCharsets.UTF_8));
        var signingKey = resources.hmac(kService, HttpSigning.TERMINATOR_BYTES);
        return resources.hmac(signingKey, stringToSign);
    }
}
