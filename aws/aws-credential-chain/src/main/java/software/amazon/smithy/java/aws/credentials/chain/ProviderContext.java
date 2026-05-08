/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.concurrent.ScheduledExecutorService;
import software.amazon.smithy.java.context.Context;

/**
 * Context passed to {@link AwsCredentialProvider#create(ProviderContext)} during chain assembly.
 *
 * <p>Carries shared resources that providers may use. Currently provides:
 * <ul>
 *   <li>A shared {@link ScheduledExecutorService} for background credential refresh tasks.</li>
 *   <li>A {@link Context} property bag for sharing data between providers (e.g., a parsed
 *       config file).</li>
 * </ul>
 *
 * @param executor shared executor for background refresh.
 * @param properties shared property bag for cross-provider data.
 */
public record ProviderContext(ScheduledExecutorService executor, Context properties) {}
