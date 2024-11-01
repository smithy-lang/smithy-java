/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

import java.time.Duration;

record RefreshRetryTokenRequestImpl(RetryToken token, Throwable failure, Duration suggestedDelay)
    implements RefreshRetryTokenRequest {}
