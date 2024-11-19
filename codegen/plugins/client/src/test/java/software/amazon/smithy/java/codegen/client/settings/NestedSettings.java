/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.settings;

import software.amazon.smithy.java.client.core.Client;

public interface NestedSettings<B extends Client.Builder<?, B>> extends AbSetting<B>, Nested<B> {
}
