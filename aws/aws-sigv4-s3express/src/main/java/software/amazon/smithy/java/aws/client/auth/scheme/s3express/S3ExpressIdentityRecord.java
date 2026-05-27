/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import java.time.Instant;

record S3ExpressIdentityRecord(
        String accessKeyId,
        String secretAccessKey,
        String sessionToken,
        Instant expirationTime) implements S3ExpressIdentity {}
