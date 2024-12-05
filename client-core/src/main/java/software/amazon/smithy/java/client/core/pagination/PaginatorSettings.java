/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination;

import software.amazon.smithy.java.client.core.RequestOverrideConfig;

/**
 * Common settings for all paginators.
 */
interface PaginatorSettings {
    /**
     * Set the total max number of items to retrieve via pagination.
     *
     * <p>Paginators will adjust the requested number of items in individual requests
     * to avoid exceeding this value.
     *
     * @param maxItems maximum number of items for the paginator to retrieve
     */
    void maxItems(int maxItems);

    /**
     * Set the override config to use for requests.
     *
     * @param overrideConfig override config to use for requests
     */
    void overrideConfig(RequestOverrideConfig overrideConfig);
}
