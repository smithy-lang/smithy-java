/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.mcp.bundle.api;

import java.util.List;
import software.amazon.smithy.mcp.bundle.api.model.Bundle;
import software.amazon.smithy.mcp.bundle.api.model.BundleMetadata;

public interface Registry {

    final class RegistryEntry {
        private final String title;
        private final BundleMetadata bundleMetadata;

        public RegistryEntry(String title, BundleMetadata bundleMetadata) {
            this.title = title;
            this.bundleMetadata = bundleMetadata;
        }

        /**
         * Provides a human-readable title for the bundle for display purposes.
         *
         * @return the human-facing title of the registry entry
         */
        public String getTitle() {
            return title;
        }

        /**
         * @return the Bundle metadata
         */
        public BundleMetadata getBundleMetadata() {
            return bundleMetadata;
        }
    }

    String name();

    List<RegistryEntry> listMcpBundles();

    default List<RegistryTool> searchTools(String query, int numberOfTools) {
        return List.of();
    }

    Bundle getMcpBundle(String id);

}
