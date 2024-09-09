/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal;

public final class JsonIteratorPool {

    private static final ThreadLocal<JsonIterator> slot1 = new ThreadLocal<>();
    private static final ThreadLocal<JsonIterator> slot2 = new ThreadLocal<>();

    public static JsonIterator borrowJsonIterator() {
        JsonIterator iter = slot1.get();
        if (iter != null) {
            slot1.set(null);
            return iter;
        }
        iter = slot2.get();
        if (iter != null) {
            slot2.set(null);
            return iter;
        }
        iter = JsonIterator.parse(new byte[512], 0, 0);
        return iter;
    }

    public static void returnJsonIterator(JsonIterator iter) {
        if (slot1.get() == null) {
            slot1.set(iter);
        } else if (slot2.get() == null) {
            slot2.set(iter);
        }
    }
}
