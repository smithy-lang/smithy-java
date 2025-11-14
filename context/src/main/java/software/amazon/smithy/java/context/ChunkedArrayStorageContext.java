/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.context;

/**
 * Context implementation using chunked arrays for efficient storage and access.
 *
 * <p>Keys are stored in 32-element chunks, allocated lazily as needed. This provides performance close to indexing
 * into a flat array, while being more efficient and lazy with allocations (copies are ~4.5x faster than a map,
 * getting a key ~2x faster, etc.).
 */
final class ChunkedArrayStorageContext implements Context {

    private static final int CHUNK_SIZE = 32;
    private static final int CHUNK_SHIFT = Integer.numberOfTrailingZeros(CHUNK_SIZE);
    private static final int CHUNK_MASK = CHUNK_SIZE - 1;

    private Object[][] chunks = new Object[4][];
    private int numChunks;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        int chunkIdx = key.id >> CHUNK_SHIFT;
        Object[] chunk = chunkIdx < chunks.length ? chunks[chunkIdx] : null;
        return chunk != null ? (T) chunk[key.id & CHUNK_MASK] : null;
    }

    @Override
    public <T> Context put(Key<T> key, T value) {
        int id = key.id;
        int chunkIdx = id >> CHUNK_SHIFT;

        if (chunkIdx >= chunks.length) {
            growChunksArray(chunkIdx);
        }

        Object[] chunk = getChunk(chunkIdx);
        chunk[id & CHUNK_MASK] = value;
        return this;
    }

    private void growChunksArray(int chunkIdx) {
        int newSize = Math.max(chunks.length * 2, chunkIdx + 1);
        Object[][] newChunks = new Object[newSize][];
        System.arraycopy(chunks, 0, newChunks, 0, chunks.length);
        chunks = newChunks;
    }

    private Object[] getChunk(int chunkIdx) {
        Object[] chunk = chunks[chunkIdx];
        return chunk != null ? chunk : createChunk(chunkIdx);
    }

    private Object[] createChunk(int chunkIdx) {
        Object[] chunk = new Object[CHUNK_SIZE];
        chunks[chunkIdx] = chunk;

        // Update numChunks to track highest allocated chunk
        if (chunkIdx >= numChunks) {
            numChunks = chunkIdx + 1;
        }

        return chunk;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void copyTo(Context target) {
        if (target instanceof UnmodifiableContext) {
            throw new UnsupportedOperationException("Cannot copy to an unmodifiable context");
        }

        ChunkedArrayStorageContext other = (ChunkedArrayStorageContext) target;

        // Ensure target has capacity
        if (numChunks > other.chunks.length) {
            Object[][] newChunks = new Object[numChunks][];
            System.arraycopy(other.chunks, 0, newChunks, 0, other.chunks.length);
            other.chunks = newChunks;
        }

        if (numChunks > other.numChunks) {
            other.numChunks = numChunks;
        }

        // Copy chunks
        for (int chunkIdx = 0; chunkIdx < numChunks; chunkIdx++) {
            Object[] sourceChunk = chunks[chunkIdx];
            if (sourceChunk == null) {
                continue;
            }

            Object[] targetChunk = other.chunks[chunkIdx];
            if (targetChunk == null) {
                targetChunk = new Object[CHUNK_SIZE];
                other.chunks[chunkIdx] = targetChunk;
                // Target chunk is empty, can use fast path
                System.arraycopy(sourceChunk, 0, targetChunk, 0, CHUNK_SIZE);

                // Fix up mutable values
                int baseId = chunkIdx << CHUNK_SHIFT;
                for (int offset = 0; offset < CHUNK_SIZE; offset++) {
                    Object v = targetChunk[offset];
                    if (v != null) {
                        Key<Object> k = Key.KEYS.get(baseId + offset);
                        Object copied = k.copyValue(v);
                        if (copied != v) {
                            targetChunk[offset] = copied;
                        }
                    }
                }
            } else {
                // Target chunk exists, merge values element-by-element
                int baseId = chunkIdx << CHUNK_SHIFT;
                for (int offset = 0; offset < CHUNK_SIZE; offset++) {
                    Object v = sourceChunk[offset];
                    if (v != null) {
                        Key<Object> k = Key.KEYS.get(baseId + offset);
                        targetChunk[offset] = k.copyValue(v);
                    }
                }
            }
        }
    }
}
