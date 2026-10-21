/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;

final class SigV4aEventSigner extends AwsEventSigner {

    SigV4aEventSigner(AwsCredentialsIdentity identity, Context signerContext, String seedSignature) {
        super("AWS4-ECDSA-P256-SHA256-PAYLOAD", identity, signerContext, seedSignature);
    }

    @Override
    String scopeRegion() {
        return null;
    }

    @Override
    byte[] sign(byte[] stringToSign, String region, String service, SigningResources resources) {
        var key = SigV4aKeyDerivation.derive(identity.accessKeyId(), identity.secretAccessKey(), resources);
        return resources.ecdsaSign(key, stringToSign, 0, stringToSign.length);
    }
}
