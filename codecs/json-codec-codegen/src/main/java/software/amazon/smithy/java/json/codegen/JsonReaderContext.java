/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import software.amazon.smithy.java.codegen.rt.SpecializedCodecRegistry;
import software.amazon.smithy.java.json.smithy.JsonParseState;

/**
 * Mutable read context passed to generated deserializers.
 * Extends {@link JsonParseState} to provide parsing result storage,
 * and carries the input buffer, position, end, and codec registry.
 *
 * <p>Fields are public for direct access from generated code.
 */
@SuppressFBWarnings(value = {"PA_PUBLIC_PRIMITIVE_ATTRIBUTE", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"},
        justification = "Public fields intentional for JIT optimization in generated code")
public final class JsonReaderContext extends JsonParseState {

    public byte[] buf;
    public int pos;
    public int end;
    public SpecializedCodecRegistry registry;

    public JsonReaderContext(byte[] buf, int pos, int end, SpecializedCodecRegistry registry) {
        this.buf = buf;
        this.pos = pos;
        this.end = end;
        this.registry = registry;
    }
}
