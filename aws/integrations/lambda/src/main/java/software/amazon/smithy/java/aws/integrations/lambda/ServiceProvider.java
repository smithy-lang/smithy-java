/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.integrations.lambda;

import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyUnstableApi;

/*
 * The interface for registering a service implementation with the Lambda endpoint.
 */
@SmithyUnstableApi
public interface ServiceProvider {
    Service get();
}
