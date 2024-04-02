/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.identity;

import java.util.Objects;
import java.util.Optional;

/**
 * AWS credentials used for accessing services: AWS access key ID, secret access key, and session tokens. These
 * credentials are used to securely sign requests to services (e.g., AWS services) that use them for authentication.
 *
 * <p>For more details on AWS access keys, see:
 * <a href="https://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html#access-keys-and-secret-access-keys">
 * https://docs.aws.amazon.com/general/latest/gr/aws-sec-cred-types.html#access-keys-and-secret-access-keys</a></p>
 */
public interface AwsCredentialsIdentity extends Identity {
    /**
     * Retrieve the AWS access key, used to identify the user interacting with services.
     */
    String accessKeyId();

    /**
     * Retrieve the AWS secret access key, used to authenticate the user interacting with services.
     */
    String secretAccessKey();

    /**
     * Retrieve the AWS session token. This token is retrieved from an AWS token service, and is used for authenticating that this
     * user has received temporary permission to access some resource.
     */
    Optional<String> sessionToken();

    /**
     * Constructs a new session credentials object, with the specified AWS access key and secret key.
     *
     * @param accessKeyId The AWS access key, used to identify the user interacting with services.
     * @param secretAccessKey The AWS secret access key, used to authenticate the user interacting with services.
     * @return the created identity.
     */
    static AwsCredentialsIdentity create(String accessKeyId, String secretAccessKey) {
        return create(accessKeyId, secretAccessKey, null);
    }

    /**
     * Constructs a new session credentials object, with the specified AWS access key, secret key, and session token.
     *
     * @param accessKeyId The AWS access key, used to identify the user interacting with services.
     * @param secretAccessKey The AWS secret access key, used to authenticate the user interacting with services.
     * @param sessionToken The AWS session token, used for authenticating temporary access some resource.
     */
    static AwsCredentialsIdentity create(String accessKeyId, String secretAccessKey, String sessionToken) {
        return new AwsCredentialsIdentityRecord(
            Objects.requireNonNull(accessKeyId, "accessKeyId is null"),
            Objects.requireNonNull(secretAccessKey, "secretAccessKey is null"),
            Optional.ofNullable(sessionToken)
        );
    }
}
