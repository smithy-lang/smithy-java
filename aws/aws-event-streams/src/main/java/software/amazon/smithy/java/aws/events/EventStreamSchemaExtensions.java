/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.util.ArrayList;
import java.util.List;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Pre-computes AWS event-stream serde data on {@link Schema} objects so the per-frame hot path can avoid
 * repeated trait lookups, member walks, and {@code withFilteredMembers} wrapper allocations.
 *
 * <p>The encoder ({@link AwsEventShapeEncoder}) and decoder ({@link AwsEventShapeDecoder}) classify the
 * members of an event's target structure into three disjoint groups on every frame:
 *
 * <ul>
 *   <li><b>event-payload</b> The single member carrying {@code @eventPayload}, serialized directly as the
 *       message payload (blob/string/other).</li>
 *   <li><b>header</b> Members carrying {@code @eventHeader}, serialized into the message headers.</li>
 *   <li><b>payload</b> Everything else, serialized into the message body via the protocol codec.</li>
 * </ul>
 */
@SmithyInternalApi
public final class EventStreamSchemaExtensions
        implements SchemaExtensionProvider<EventStreamSchemaExtensions.EventStreamExt> {

    /**
     * Extension key for AWS event-stream pre-computed data.
     */
    public static final SchemaExtensionKey<EventStreamExt> KEY = new SchemaExtensionKey<>();

    private static final Schema[] NO_SCHEMAS = new Schema[0];
    private static final Binding[] NO_BINDINGS = new Binding[0];

    /**
     * Per-member event-stream binding kind, resolved once from the member's traits.
     *
     * <p>Mirrors {@code HttpBindingSchemaExtensions.Binding}: it lets the per-frame serializer dispatch each
     * member with a single array load on {@code bindings[memberIndex]} instead of a chain of {@code hasTrait}
     * checks.
     */
    enum Binding {
        /**
         * Serialized into the message headers ({@code @eventHeader}).
         */
        HEADER,
        /**
         * Serialized into the message body via the protocol codec (default).
         */
        PAYLOAD,
        /**
         * Serialized directly as the message payload ({@code @eventPayload}).
         */
        EVENT_PAYLOAD,
        /**
         * The event-stream member of an initial-request/response struct ({@code @streaming} target).
         */
        STREAMING
    }

    /**
     * Pre-computed event-stream partition for one structure/union schema.
     *
     * <p>This is the event-stream analog of {@code HttpBindingSchemaExtensions}'s request/response bindings:
     * each member is routed to the headers or the body (or, for event payloads, directly to the payload),
     * exactly like HTTP binding routes members to headers vs. body. The {@link #bindings} array, indexed by
     * {@link Schema#memberIndex()}, lets the serializer dispatch each member with one array load.
     *
     * @param eventPayloadMember the single {@code @eventPayload} member, or {@code null} if none.
     * @param headerMembers      members carrying {@code @eventHeader} (empty if none).
     * @param hasPayloadMembers  whether any member is serialized into the codec body (neither a header member
     *                           nor the event-payload member).
     * @param streamingMember    the member whose target carries {@code @streaming} (the event-stream member of an
     *                           initial-request/response struct), or {@code null} if none.
     * @param bindings           per-member {@link Binding} indexed by {@link Schema#memberIndex()}.
     */
    public record EventStreamExt(
            Schema eventPayloadMember,
            Schema[] headerMembers,
            boolean hasPayloadMembers,
            Schema streamingMember,
            Binding[] bindings) {

        /**
         * Whether this struct has a dedicated {@code @eventPayload} member.
         */
        boolean hasEventPayload() {
            return eventPayloadMember != null;
        }

        /**
         * Whether this struct has any {@code @eventHeader} member.
         */
        boolean hasHeaderMembers() {
            return headerMembers.length > 0;
        }

        /**
         * Whether this struct is an initial-request/response struct (has an event-stream member).
         */
        boolean isInitialEvent() {
            return streamingMember != null;
        }
    }

    @Override
    public SchemaExtensionKey<EventStreamExt> key() {
        return KEY;
    }

    /**
     * Look up the {@link EventStreamExt} for an event struct schema. The provider populates every structure
     * and union, so a null here means the provider was not registered or the schema is not a struct/union;
     * either is a programming error, surfaced as an {@link IllegalStateException} rather than a bare NPE.
     */
    static EventStreamExt extensionOf(Schema schema) {
        var ext = schema.getExtension(KEY);
        if (ext == null) {
            throw new IllegalStateException("Schema " + schema.id() + " has no event-stream extension");
        }
        return ext;
    }

    @Override
    public EventStreamExt provide(Schema schema) {
        // The encoder looks up the extension via the union member schema while the decoder uses the member's
        // target. Delegate members to their target so the data is computed and cached once, on the target.
        if (schema.isMember()) {
            return schema.memberTarget().getExtension(KEY);
        }
        var type = schema.type();
        if (type != ShapeType.STRUCTURE && type != ShapeType.UNION) {
            return null;
        }

        Schema eventPayloadMember = null;
        Schema streamingMember = null;
        List<Schema> headerMembers = null;
        int payloadMemberCount = 0;

        var members = schema.members();
        Binding[] bindings = members.isEmpty() ? NO_BINDINGS : new Binding[members.size()];

        for (Schema member : members) {
            int idx = member.memberIndex();
            // Precedence (streaming > eventPayload > eventHeader > body) assumes a well-formed model where
            // these event traits are mutually exclusive on a member; the encoder and decoder likewise treat
            // the categories as disjoint.
            if (member.memberTarget().hasTrait(TraitKey.STREAMING_TRAIT)) {
                // The streaming member of an initial-request/response struct. It is excluded from both the
                // header and payload groups (the initial event serializes every other member).
                streamingMember = member;
                bindings[idx] = Binding.STREAMING;
            } else if (member.hasTrait(TraitKey.EVENT_PAYLOAD_TRAIT)) {
                eventPayloadMember = member;
                bindings[idx] = Binding.EVENT_PAYLOAD;
            } else if (member.hasTrait(TraitKey.EVENT_HEADER_TRAIT)) {
                if (headerMembers == null) {
                    headerMembers = new ArrayList<>();
                }
                headerMembers.add(member);
                bindings[idx] = Binding.HEADER;
            } else {
                payloadMemberCount++;
                bindings[idx] = Binding.PAYLOAD;
            }
        }

        return new EventStreamExt(
                eventPayloadMember,
                toArray(headerMembers),
                payloadMemberCount > 0,
                streamingMember,
                bindings);
    }

    private static Schema[] toArray(List<Schema> list) {
        return list == null ? NO_SCHEMAS : list.toArray(new Schema[0]);
    }

    /**
     * A proxy over a struct that serializes only the members matching a target {@link Binding}, routing each
     * member with a single array load on the precomputed {@code bindings} table.
     *
     * <p>This is the event-stream analog of {@code StructBodyProxy} in http-binding: same
     * {@link InterceptingSerializer} shape, but the {@code before(schema)} decision is an array index rather
     * than a {@code hasTrait} call, which is what removes the per-frame trait walk.
     */
    record BindingFilteredStruct(SerializableStruct delegate, Binding[] bindings, Binding keep)
            implements SerializableStruct {
        @Override
        public Schema schema() {
            return delegate.schema();
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            delegate.serializeMembers(new InterceptingSerializer() {
                @Override
                protected ShapeSerializer before(Schema schema) {
                    return bindings[schema.memberIndex()] == keep ? serializer : ShapeSerializer.nullSerializer();
                }
            });
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return bindings[member.memberIndex()] == keep ? delegate.getMemberValue(member) : null;
        }
    }

    /**
     * A proxy that serializes every member <em>except</em> the one with the given {@link Binding},  used by the
     * initial-event path to serialize the whole struct minus its {@code @streaming} member.
     */
    record BindingExcludingStruct(SerializableStruct delegate, Binding[] bindings, Binding exclude)
            implements SerializableStruct {
        @Override
        public Schema schema() {
            return delegate.schema();
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            delegate.serializeMembers(new InterceptingSerializer() {
                @Override
                protected ShapeSerializer before(Schema schema) {
                    return bindings[schema.memberIndex()] != exclude ? serializer : ShapeSerializer.nullSerializer();
                }
            });
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return bindings[member.memberIndex()] != exclude ? delegate.getMemberValue(member) : null;
        }
    }
}
