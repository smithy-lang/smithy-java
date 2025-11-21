/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.settings;

import java.util.Objects;
import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.model.Model;

/**
 * Sets the required Smithy model of a dynamic client.
 */
public interface ModelSetting<B extends ClientSetting<B>> extends ClientSetting<B> {
    /**
     * Smithy model used with a client.
     */
    Context.Key<Model> MODEL = Context.key("Smithy model");

    /**
     * Set the Smithy model used with a dynamic client.
     *
     * @param model Smithy model to set.
     */
    default B model(Model model) {
        return putConfig(MODEL, Objects.requireNonNull(model, "model cannot be null"));
    }
}
