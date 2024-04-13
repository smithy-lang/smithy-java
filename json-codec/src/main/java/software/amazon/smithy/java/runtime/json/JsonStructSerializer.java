/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.output.JsonStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.RequiredWriteSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.StructSerializer;
import software.amazon.smithy.model.traits.JsonNameTrait;

class JsonStructSerializer implements StructSerializer {

    private final boolean useJsonName;
    private final ShapeSerializer parent;
    private final JsonStream stream;
    private boolean wroteValues = false;

    JsonStructSerializer(ShapeSerializer parent, JsonStream stream, boolean useJsonName) {
        this.parent = parent;
        this.stream = stream;
        this.useJsonName = useJsonName;
    }

    private String getMemberName(SdkSchema member) {
        if (useJsonName && member.hasTrait(JsonNameTrait.class)) {
            return member.getTrait(JsonNameTrait.class).getValue();
        } else {
            return member.memberName();
        }
    }

    void startMember(SdkSchema member) throws IOException {
        // Write commas when needed.
        if (wroteValues) {
            stream.writeMore();
        } else {
            wroteValues = true;
        }
        stream.writeObjectField(getMemberName(member));
    }

    @Override
    public void member(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
        try {
            startMember(member);
            // Throw if a value isn't written.
            RequiredWriteSerializer.assertWrite(
                parent,
                () -> new SdkException("Structure member did not write a value for " + member),
                memberWriter
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
