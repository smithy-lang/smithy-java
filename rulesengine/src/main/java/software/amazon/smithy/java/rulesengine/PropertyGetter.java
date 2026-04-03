/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

/**
 * Lightweight alternative to {@link java.util.Map} for rules engine objects that support
 * property access by name.
 */
@FunctionalInterface
public interface PropertyGetter {
    /**
     * Gets a property value by name.
     *
     * @param name the property name
     * @return the property value, or {@code null} if not found
     */
    Object getProperty(String name);
}
