/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.settings;

import software.amazon.smithy.java.client.core.ClientSetting;

public interface NestedSettings<B extends ClientSetting<B>> extends AbSetting<B>, Nested<B> {
}
