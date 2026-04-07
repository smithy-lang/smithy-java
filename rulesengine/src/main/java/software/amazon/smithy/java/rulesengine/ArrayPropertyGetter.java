/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

/**
 * Lightweight PropertyGetter backed by parallel key/value arrays.
 * More efficient than Map for small fixed-key lookups (linear scan beats hashing for ~4 entries).
 */
record ArrayPropertyGetter(String[] keys, Object[] values) implements PropertyGetter {
    @Override
    public Object getProperty(String name) {
        for (int i = 0; i < keys.length; i++) {
            if (name.equals(keys[i])) {
                return values[i];
            }
        }
        return null;
    }
}
