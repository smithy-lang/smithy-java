/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.util.concurrent.atomic.AtomicReferenceArray;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Provides bounded, shared member mappings for shape transcoding.
 */
@SmithyInternalApi
public final class ShapeTranscoderSchemaExtensions
        implements SchemaExtensionProvider<ShapeTranscoderSchemaExtensions.TranscodingExtension> {

    static final SchemaExtensionKey<TranscodingExtension> KEY = new SchemaExtensionKey<>();
    private static final int CACHE_SIZE = 8;
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    @Override
    public SchemaExtensionKey<TranscodingExtension> key() {
        return KEY;
    }

    @Override
    public TranscodingExtension provide(Schema schema) {
        return switch (schema.type()) {
            case STRUCTURE, UNION -> new TranscodingExtension(schema);
            default -> null;
        };
    }

    static MemberMapping mapping(Schema source, Schema target) {
        var extension = source.getExtension(KEY);
        return extension == null ? new MemberMapping(source, target) : extension.mappingTo(target);
    }

    /**
     * Safely published through final fields. Concurrent extension computation may create independent holders,
     * but entries only avoid redundant work and never affect mapping behavior.
     */
    @SmithyInternalApi
    public static final class TranscodingExtension {
        private final Schema source;
        private final AtomicReferenceArray<MemberMapping> mappings = new AtomicReferenceArray<>(CACHE_SIZE);

        private TranscodingExtension(Schema source) {
            this.source = source;
        }

        private MemberMapping mappingTo(Schema target) {
            var index = cacheIndex(target);
            var current = mappings.get(index);
            if (current != null && current.matches(source, target)) {
                return current;
            }

            var mapping = new MemberMapping(source, target);
            if (mappings.compareAndSet(index, current, mapping)) {
                return mapping;
            }

            current = mappings.get(index);
            return current != null && current.matches(source, target) ? current : mapping;
        }

        private static int cacheIndex(Schema target) {
            var hash = System.identityHashCode(target);
            return (hash ^ (hash >>> 16)) & CACHE_MASK;
        }
    }

    static final class MemberMapping {
        private final Schema source;
        private final Schema target;
        private final Schema[] sourceMembers;
        private final Schema[] targetMembers;

        private MemberMapping(Schema source, Schema target) {
            this.source = source;
            this.target = target;
            sourceMembers = source.members().toArray(Schema[]::new);
            targetMembers = new Schema[sourceMembers.length];
            for (var i = 0; i < sourceMembers.length; i++) {
                targetMembers[i] = target.member(sourceMembers[i].memberName());
            }
        }

        boolean matches(Schema source, Schema target) {
            return this.source == source && this.target == target;
        }

        Schema targetMember(Schema sourceMember) {
            var index = sourceMember.memberIndex();
            if (index >= 0 && index < sourceMembers.length && sourceMembers[index] == sourceMember) {
                return targetMembers[index];
            }
            return target.member(sourceMember.memberName());
        }
    }
}
