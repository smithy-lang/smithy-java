/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * A proxy over a delegate structure that serializes on members with a BODY binding to a target serializer.
 *
 * @param delegate Input/Output/Error structure to proxy.
 * @param bindings Direction-specific per-member binding kinds (from RequestBinding or ResponseBinding).
 */
record StructBodyProxy(SerializableStruct delegate, HttpBindingSchemaExtensions.Binding[] bindings)
        implements SerializableStruct {
    @Override
    public Schema schema() {
        return delegate.schema();
    }

    @Override
    public void serializeMembers(ShapeSerializer codecMemberSerializer) {
        delegate.serializeMembers(new BodyMemberSerializer(codecMemberSerializer));
    }

    @Override
    public <T> T getMemberValue(Schema member) {
        return delegate.getMemberValue(member);
    }

    private final class BodyMemberSerializer extends InterceptingSerializer {
        private final ShapeSerializer inner;

        BodyMemberSerializer(ShapeSerializer inner) {
            this.inner = inner;
        }

        @Override
        protected ShapeSerializer before(Schema schema) {
            return bindings[schema.memberIndex()] == HttpBindingSchemaExtensions.Binding.BODY
                    ? inner
                    : ShapeSerializer.nullSerializer();
        }
    }
}
