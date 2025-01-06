/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import java.time.Instant;

record AwsCredentialsIdentityRecord(
        String accessKeyId,
        String secretAccessKey,
        String sessionToken,
        Instant expirationTime) implements AwsCredentialsIdentity {}
