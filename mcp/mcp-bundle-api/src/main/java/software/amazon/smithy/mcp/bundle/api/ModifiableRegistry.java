/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.mcp.bundle.api;

import software.amazon.smithy.mcp.bundle.api.model.Bundle;

public interface ModifiableRegistry<T> extends Registry {
    void publishBundle(Bundle bundle, T args);
}
