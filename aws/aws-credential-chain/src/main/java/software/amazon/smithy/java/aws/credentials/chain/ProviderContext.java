/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.concurrent.ScheduledExecutorService;
import software.amazon.smithy.java.context.Context;

/**
 * Context passed to {@link ChainIdentityProvider#create)} during chain assembly.
 *
 * @param executor shared executor for background refresh.
 * @param properties shared property bag for cross-provider data.
 */
public record ProviderContext(ScheduledExecutorService executor, Context properties) {}
