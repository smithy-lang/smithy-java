/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.net.URI;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;

public record ServiceProtocolResolutionRequest(URI uri, HttpHeaders headers, Context requestContext, String method) {
}
