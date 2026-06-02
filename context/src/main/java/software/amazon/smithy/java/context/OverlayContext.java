/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

final class OverlayContext implements Context {

    private static final int CHUNK_SIZE = 32;
    private static final int CHUNK_SHIFT = Integer.numberOfTrailingZeros(CHUNK_SIZE);
    private static final int CHUNK_MASK = CHUNK_SIZE - 1;

    private final Context parent;
    // Lazily-allocated write overlay (null until the first put). Mirrors ChunkedArrayStorageContext's
    // layout so overlay reads are an array index, not a map lookup.
    private Object[][] chunks;
    private int numChunks;

    OverlayContext(Context parent) {
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        int id = key.id;
        int chunkIdx = id >> CHUNK_SHIFT;
        if (chunks != null && chunkIdx < chunks.length) {
            Object[] chunk = chunks[chunkIdx];
            if (chunk != null) {
                Object v = chunk[id & CHUNK_MASK];
                if (v != null) {
                    return (T) v;
                }
            }
        }
        // Miss in the overlay: read through to the immutable parent.
        return parent.get(key);
    }

    @Override
    public <T> Context put(Key<T> key, T value) {
        int id = key.id;
        int chunkIdx = id >> CHUNK_SHIFT;

        if (chunks == null) {
            chunks = new Object[chunkIdx + 1][];
        } else if (chunkIdx >= chunks.length) {
            Object[][] newChunks = new Object[chunkIdx + 1][];
            System.arraycopy(chunks, 0, newChunks, 0, chunks.length);
            chunks = newChunks;
        }

        Object[] chunk = chunks[chunkIdx];
        if (chunk == null) {
            chunk = new Object[CHUNK_SIZE];
            chunks[chunkIdx] = chunk;
            if (chunkIdx >= numChunks) {
                numChunks = chunkIdx + 1;
            }
        }
        chunk[id & CHUNK_MASK] = value;
        return this;
    }

    @Override
    public void copyTo(Context target) {
        if (target instanceof UnmodifiableContext) {
            throw new UnsupportedOperationException("Cannot copy to an unmodifiable context");
        }
        // Flatten parent-then-overlay so the merged view (parent defaults, overlaid by per-call writes)
        // is reproduced in the target. Parent first, overlay second => overlay writes win, matching get().
        parent.copyTo(target);
        if (chunks == null) {
            return;
        }
        for (int chunkIdx = 0; chunkIdx < numChunks; chunkIdx++) {
            Object[] chunk = chunks[chunkIdx];
            if (chunk == null) {
                continue;
            }
            int baseId = chunkIdx << CHUNK_SHIFT;
            for (int offset = 0; offset < CHUNK_SIZE; offset++) {
                Object v = chunk[offset];
                if (v != null) {
                    @SuppressWarnings("unchecked")
                    Key<Object> k = (Key<Object>) Key.KEYS.get(baseId + offset);
                    target.put(k, k.copyValue(v));
                }
            }
        }
    }
}
