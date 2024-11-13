/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.retries.sdkadapter;

import software.amazon.smithy.java.runtime.retries.api.RetryToken;

record SdkRetryToken(software.amazon.awssdk.retries.api.RetryToken delegate) implements RetryToken {}
