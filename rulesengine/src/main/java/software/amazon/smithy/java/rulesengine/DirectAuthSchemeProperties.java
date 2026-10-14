/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

/**
 * Reusable flat view of one compiled auth-scheme struct.
 *
 * <p>The holder belongs to one {@link BytecodeEvaluator} and never escapes a resolution. Keys remain
 * on the immutable compiled node; only the values need evaluator-owned scratch.
 */
final class DirectAuthSchemeProperties implements PropertyGetter {
    private String[] keys;
    private Object[] values = new Object[8];
    private int size;

    Object[] reset(String[] keys) {
        this.keys = keys;
        this.size = keys.length;
        if (values.length < size) {
            values = new Object[Integer.highestOneBit(size - 1) << 1];
        }
        return values;
    }

    @Override
    public Object getProperty(String name) {
        String[] localKeys = keys;
        Object[] localValues = values;
        for (int i = 0; i < size; i++) {
            if (name.equals(localKeys[i])) {
                return localValues[i];
            }
        }
        return null;
    }
}
