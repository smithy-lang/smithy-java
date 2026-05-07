/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Context passed to {@link AwsCredentialProvider#create(ProviderContext)} during chain assembly.
 *
 * <p>Carries shared resources that providers may use.
 *
 * @param executor Possibly null custom executor for resolving credentials.
 */
public record ProviderContext(ScheduledExecutorService executor) {}
