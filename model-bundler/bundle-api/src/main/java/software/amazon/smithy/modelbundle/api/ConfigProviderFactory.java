/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import software.amazon.smithy.java.core.serde.document.Document;

public interface ConfigProviderFactory {
    String identifier();

    ConfigProvider<?> createAuthFactory(Document input);
}
