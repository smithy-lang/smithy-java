/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

<<<<<<<< HEAD:aws/client-http/src/main/java/software/amazon/smithy/java/aws/runtime/client/http/auth/identity/AwsCredentialsIdentityRecord.java
package software.amazon.smithy.java.aws.runtime.client.http.auth.identity;
========
package software.amazon.smithy.java.runtime.aws.http.auth.identity;
>>>>>>>> 45f2eee4 (Initial implementation of SigV4 auth scheme.):aws-client-http/src/main/java/software/amazon/smithy/java/runtime/aws/http/auth/identity/AwsCredentialsIdentityRecord.java

import java.util.Optional;

record AwsCredentialsIdentityRecord(
    String accessKeyId, String secretAccessKey,
    Optional<String> sessionToken
) implements AwsCredentialsIdentity {
}
