/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * A BundlePlugin applies the settings specified in a {@link software.amazon.smithy.modelbundle.api.model.Bundle}
 * on a per-call basis.
 */
public interface BundlePlugin {
    /**
     * Applies the bundle-specific settings to a client call.
     *
     * @param args per-request args specified by the bundle, or null if the bundle takes no per-request args
     * @return a {@link RequestOverrideConfig.Builder} with the settings from the bundle applied
     */
    RequestOverrideConfig.Builder buildOverride(Document args);
}
