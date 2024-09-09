/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal;

import java.nio.charset.StandardCharsets;

public final class Slice {

    private byte[] data;
    private int head;
    private int tail;
    private int hash;

    public Slice(byte[] data, int head, int tail) {
        this.data = data;
        this.head = head;
        this.tail = tail;
    }

    public void reset(byte[] data, int head, int tail) {
        this.data = data;
        this.head = head;
        this.tail = tail;
        this.hash = 0;
    }

    public byte at(int pos) {
        return data[head + pos];
    }

    public int len() {
        return tail - head;
    }

    public byte[] data() {
        return data;
    }

    public int head() {
        return head;
    }

    public int tail() {
        return tail;
    }

    public static Slice make(String str) {
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        return new Slice(data, 0, data.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof Slice slice)) {
            return false;
        } else if ((tail - head) != (slice.tail - slice.head)) {
            return false;
        } else {
            for (int i = head, j = slice.head; i < tail; i++, j++) {
                if (data[i] != slice.data[j]) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public int hashCode() {
        if (hash == 0 && tail - head > 0) {
            for (int i = head; i < tail; i++) {
                hash = 31 * hash + data[i];
            }
        }
        return hash;
    }

    @Override
    public String toString() {
        return new String(data, head, tail - head, StandardCharsets.UTF_8);
    }
}
