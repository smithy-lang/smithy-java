/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.time.Clock;
import software.amazon.smithy.java.context.Context;

/**
 * Configuration properties used by clients for configuration related to SigV4.
 *
 * <p>These properties should be set in the client configuration by a plugin (default or manually applied) as opposed
 * to {@code AuthProperties} that are set by the AuthScheme.
 */
public final class SigV4Properties {
    /**
     * Region to use for signing. For example {@code us-east-2}.
     */
    public static final Context.Key<String> REGION = Context.key("region");
    /**
     * Service name to use for signing. For example {@code lambda}.
     */
    public static final Context.Key<Clock> CLOCK = Context.key("clock");
}
