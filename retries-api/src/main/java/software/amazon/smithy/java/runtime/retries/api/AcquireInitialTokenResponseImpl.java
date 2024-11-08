/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.api;

import java.time.Duration;

record AcquireInitialTokenResponseImpl(RetryToken token, Duration delay) implements AcquireInitialTokenResponse {}
