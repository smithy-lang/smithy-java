/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

import java.util.Optional;

record AwsCredentialsIdentityRecord(
        String accessKeyId, String secretAccessKey,
        Optional<String> sessionToken
) implements AwsCredentialsIdentity {
}
