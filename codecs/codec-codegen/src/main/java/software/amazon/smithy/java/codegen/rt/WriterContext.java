/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.rt;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Mutable output buffer context for generated serializers.
 * Fields are public for direct access from generated code (JIT promotes to registers).
 *
 * <p>Subclasses (e.g. JsonWriterContext) handle pooling and format-specific state.
 */
@SuppressFBWarnings(value = {"PA_PUBLIC_PRIMITIVE_ATTRIBUTE", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"},
        justification = "Public fields intentional for JIT optimization in generated code")
public class WriterContext {

    private static final int INITIAL_CAPACITY = 512;

    public byte[] buf;
    public int pos;
    public int needsComma;
    public SpecializedCodecRegistry registry;

    public WriterContext(SpecializedCodecRegistry registry) {
        this.buf = new byte[INITIAL_CAPACITY];
        this.pos = 0;
        this.registry = registry;
    }

    public void ensureCapacity(int needed) {
        if (pos + needed > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + needed));
        }
    }

    /**
     * Ensures capacity for {@code needed} bytes starting at {@code pos} and returns
     * the (possibly reallocated) buffer. Does NOT write pos back to ctx — callers keep
     * pos in a local register and sync explicitly before helper calls.
     */
    public byte[] ensure(int pos, int needed) {
        if (pos + needed > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, pos + needed));
        }
        return buf;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, pos);
    }

    public ByteBuffer toByteBuffer() {
        return ByteBuffer.wrap(buf, 0, pos);
    }
}
