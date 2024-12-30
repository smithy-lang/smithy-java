/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.sdkv2.shapes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;

public class SdkDocumentReaderTest {
    @Test
    public void convertsNullDocuments() {
        var sdk = Document.fromNull();
        var smithy = AwsJsonProtocols.REST_JSON_1.sdkToSmithy(sdk);

        assertThat(smithy, nullValue());
    }

    @Test
    public void convertsStringDocuments() {
        var sdk = Document.fromString("hi");
        var smithy = AwsJsonProtocols.REST_JSON_1.sdkToSmithy(sdk);

        assertThat(smithy.type(), is(ShapeType.STRING));
        assertThat(smithy.asString(), equalTo("hi"));
    }

    @Test
    public void convertsNumberDocuments() {
        var sdk = Document.fromNumber(BigDecimal.ONE);
        var smithy = AwsJsonProtocols.REST_JSON_1.sdkToSmithy(sdk);

        assertThat(smithy.type(), is(ShapeType.BIG_DECIMAL));
        assertThat(smithy.asBigDecimal(), equalTo(BigDecimal.ONE));
    }

    @Test
    public void convertsBooleanDocuments() {
        var sdk = Document.fromBoolean(true);
        var smithy = AwsJsonProtocols.REST_JSON_1.sdkToSmithy(sdk);

        assertThat(smithy.type(), is(ShapeType.BOOLEAN));
        assertThat(smithy.asBoolean(), is(true));
    }

    @Test
    public void convertsListDocuments() {
        var sdk = Document.fromList(List.of(Document.fromString("a"), Document.fromString("b")));
        var smithy = AwsJsonProtocols.REST_JSON_1.sdkToSmithy(sdk);

        assertThat(smithy.type(), is(ShapeType.LIST));
        assertThat(smithy.asObject(), equalTo(List.of("a", "b")));
    }

    @Test
    public void convertsMapDocuments() {
        var sdk = Document.fromMap(Map.of("a", Document.fromString("a1"), "b", Document.fromString("b1")));
        var smithy = AwsJsonProtocols.REST_JSON_1.sdkToSmithy(sdk);

        assertThat(smithy.type(), is(ShapeType.MAP));
        assertThat(smithy.asObject(), equalTo(Map.of("a", "a1", "b", "b1")));
    }

    @Test
    public void deserializesUsingJsonName() {
        var sdk = Document.fromMap(Map.of("A", Document.fromString("a1"), "B", Document.fromString("b1")));
        var smithy = AwsJsonProtocols.REST_JSON_1.sdkToSmithy(sdk);
        var schema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("a", PreludeSchemas.STRING, new JsonNameTrait("A"))
                .putMember("b", PreludeSchemas.STRING, new JsonNameTrait("B"))
                .build();

        List<Schema> members = new ArrayList<>();
        try (var deser = smithy.createDeserializer()) {
            deser.readStruct(schema, null, new ShapeDeserializer.StructMemberConsumer<Object>() {
                @Override
                public void accept(Object state, Schema memberSchema, ShapeDeserializer memberDeserializer) {
                    members.add(memberSchema);
                }

                @Override
                public void unknownMember(Object state, String memberName) {
                    throw new RuntimeException("Unexpected member: " + memberName);
                }
            });
        }

        assertThat(members, hasSize(2));
        assertThat(members.get(0).memberName(), equalTo("a"));
        assertThat(members.get(1).memberName(), equalTo("b"));
    }
}
