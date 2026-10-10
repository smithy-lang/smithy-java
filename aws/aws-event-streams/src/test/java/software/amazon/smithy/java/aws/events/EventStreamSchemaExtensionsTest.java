/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.events.model.BlobEvent;
import software.amazon.smithy.java.aws.events.model.BodyAndHeaderEvent;
import software.amazon.smithy.java.aws.events.model.HeadersOnlyEvent;
import software.amazon.smithy.java.aws.events.model.StringEvent;
import software.amazon.smithy.java.aws.events.model.StructureEvent;
import software.amazon.smithy.java.aws.events.model.TestOperationInput;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;

class EventStreamSchemaExtensionsTest {

    private static final EventStreamSchemaExtensions PROVIDER = new EventStreamSchemaExtensions();

    private static String[] memberNames(Schema[] members) {
        return Arrays.stream(members).map(Schema::memberName).toArray(String[]::new);
    }

    @Test
    void nonStructNonUnionReturnsNull() {
        assertNull(PROVIDER.provide(PreludeSchemas.STRING));
        assertNull(PROVIDER.provide(PreludeSchemas.INTEGER));
    }

    @Test
    void structEventIsAllPayload() {
        // StructureEvent { foo: String } — no event traits, so foo is a body/payload member.
        var ext = PROVIDER.provide(StructureEvent.$SCHEMA);

        assertNotNull(ext);
        assertNull(ext.eventPayloadMember());
        assertFalse(ext.hasEventPayload());
        assertFalse(ext.isInitialEvent());
        assertTrue(ext.hasPayloadMembers());
        assertFalse(ext.hasHeaderMembers());
        assertArrayEquals(new String[0], memberNames(ext.headerMembers()));
    }

    @Test
    void headersOnlyEventHasOnlyHeaderMembers() {
        // HeadersOnlyEvent { sequenceNum: Integer @eventHeader }
        var ext = PROVIDER.provide(HeadersOnlyEvent.$SCHEMA);

        assertNotNull(ext);
        assertNull(ext.eventPayloadMember());
        assertFalse(ext.hasPayloadMembers());
        assertTrue(ext.hasHeaderMembers());
        assertArrayEquals(new String[] {"sequenceNum"}, memberNames(ext.headerMembers()));
    }

    @Test
    void bodyAndHeaderEventIsPartitioned() {
        // BodyAndHeaderEvent { intMember: Integer @eventHeader, stringMember: String }
        var ext = PROVIDER.provide(BodyAndHeaderEvent.$SCHEMA);

        assertNotNull(ext);
        assertNull(ext.eventPayloadMember());
        assertTrue(ext.hasPayloadMembers());
        assertArrayEquals(new String[] {"intMember"}, memberNames(ext.headerMembers()));
    }

    @Test
    void blobEventHasEventPayloadMember() {
        // BlobEvent { payload: Blob @eventPayload }
        var ext = PROVIDER.provide(BlobEvent.$SCHEMA);

        assertNotNull(ext);
        assertTrue(ext.hasEventPayload());
        assertEquals("payload", ext.eventPayloadMember().memberName());
        assertFalse(ext.hasPayloadMembers());
        assertArrayEquals(new String[0], memberNames(ext.headerMembers()));
    }

    @Test
    void stringEventHasEventPayloadMember() {
        // StringEvent { payload: String @eventPayload }
        var ext = PROVIDER.provide(StringEvent.$SCHEMA);

        assertNotNull(ext);
        assertTrue(ext.hasEventPayload());
        assertEquals("payload", ext.eventPayloadMember().memberName());
        assertFalse(ext.hasPayloadMembers());
    }

    @Test
    void initialRequestStructHasStreamingMember() {
        // TestOperationInput { headerString @eventHeader, inputStringMember, stream: TestEventStream @streaming }
        var ext = PROVIDER.provide(TestOperationInput.$SCHEMA);

        assertNotNull(ext);
        assertTrue(ext.isInitialEvent());
        assertEquals("stream", ext.streamingMember().memberName());
        // The streaming member is excluded from both groups; headerString and inputStringMember remain.
        assertArrayEquals(new String[] {"headerString"}, memberNames(ext.headerMembers()));
        assertTrue(ext.hasPayloadMembers());
    }

    @Test
    void memberSchemaDelegatesToTarget() {
        // Looking up the extension on a union member returns the same instance as its target struct, so
        // the data is computed and cached once (encoder keys by member, decoder by target).
        var streamMember = TestOperationInput.$SCHEMA.member("stream");
        var unionMember = streamMember.memberTarget().member("structureMember");

        var viaMember = unionMember.getExtension(EventStreamSchemaExtensions.KEY);
        var viaTarget = unionMember.memberTarget().getExtension(EventStreamSchemaExtensions.KEY);

        assertNotNull(viaMember);
        assertSame(viaTarget, viaMember);
    }

    @Test
    void extensionIsCachedOnSchema() {
        // Round-trips through Schema.getExtension and returns the same cached instance.
        var first = StructureEvent.$SCHEMA.getExtension(EventStreamSchemaExtensions.KEY);
        var second = StructureEvent.$SCHEMA.getExtension(EventStreamSchemaExtensions.KEY);

        assertNotNull(first);
        assertSame(first, second);
    }
}
