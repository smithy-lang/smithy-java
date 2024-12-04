/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.mock;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * The input to a {@link MockPlugin.Builder#addMatcher} function.
 *
 * @param context Context of the call.
 * @param operation Operation being called.
 * @param input Input of the call.
 */
public record MatcherRequest(Context context, ApiOperation<?, ?> operation, SerializableStruct input) {}
