/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.mcp.bundle.api;

import java.util.List;

public interface SearchableRegistry extends Registry {

    List<RegistryTool> searchTools(String query, int numberOfTools);
}
