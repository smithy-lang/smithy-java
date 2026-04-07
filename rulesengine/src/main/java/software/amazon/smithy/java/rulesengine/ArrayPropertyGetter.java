/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

/**
 * Lightweight PropertyGetter backed by an interleaved value/key array.
 * Layout: [value0, key0, value1, key1, ...]. Keys are at odd indices.
 * More efficient than Map for small fixed-key lookups (linear scan beats hashing for ~4 entries).
 */
record ArrayPropertyGetter(Object[] data) implements PropertyGetter {
    @Override
    public Object getProperty(String name) {
        for (int i = 1; i < data.length; i += 2) {
            if (name.equals(data[i])) {
                return data[i - 1];
            }
        }
        return null;
    }
}
