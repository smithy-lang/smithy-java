/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import software.amazon.smithy.aws.traits.protocols.Ec2QueryNameTrait;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.TimestampFormatTrait;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Pre-computes URL-encoded member-name bytes and list/map metadata for AWS Query and EC2 Query protocols.
 */
@SmithyInternalApi
public final class AwsQuerySchemaExtensions
        implements SchemaExtensionProvider<AwsQuerySchemaExtensions.QueryMemberBinding> {

    public static final SchemaExtensionKey<QueryMemberBinding> KEY = new SchemaExtensionKey<>();

    private static final byte[] MEMBER_BYTES = "member".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_BYTES = "key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VALUE_BYTES = "value".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENTRY_BYTES = "entry".getBytes(StandardCharsets.UTF_8);

    /**
     * Pre-computed query binding data for a schema.
     *
     * @param awsQueryNameBytes Bytes for awsQuery member name. Null for non-members.
     * @param ec2QueryNameBytes Bytes for ec2Query member name. Null for non-members.
     * @param listFlattened     Whether this list member has xmlFlattened trait.
     * @param listMemberNameBytes Pre-computed list member name bytes (for non-flattened lists). Null if flattened.
     * @param mapFlattened      Whether this map member has xmlFlattened trait.
     * @param mapKeyNameBytes   Pre-computed map key name bytes.
     * @param mapValueNameBytes Pre-computed map value name bytes.
     * @param mapEntryNameBytes Pre-computed map entry name bytes (null if flattened).
     * @param timestampFormat   Pre-resolved timestamp format for timestamp members.
     */
    public record QueryMemberBinding(
            byte[] awsQueryNameBytes,
            byte[] ec2QueryNameBytes,
            boolean listFlattened,
            byte[] listMemberNameBytes,
            boolean mapFlattened,
            byte[] mapKeyNameBytes,
            byte[] mapValueNameBytes,
            byte[] mapEntryNameBytes,
            TimestampFormatTrait.Format timestampFormat) {}

    @Override
    public SchemaExtensionKey<QueryMemberBinding> key() {
        return KEY;
    }

    @Override
    public QueryMemberBinding provide(Schema schema) {
        if (!schema.isMember()) {
            return null;
        }

        byte[] awsName = encodeName(resolveAwsQueryName(schema));
        byte[] ec2Name = encodeName(resolveEc2QueryName(schema));

        // Pre-compute list metadata if target is a list
        boolean listFlattened = false;
        byte[] listMemberNameBytes = null;
        Schema target = schema.memberTarget();
        if (target != null && target.type() == ShapeType.LIST) {
            listFlattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            if (!listFlattened) {
                Schema listMember = target.listMember();
                if (listMember != null) {
                    var xmlName = listMember.getTrait(TraitKey.XML_NAME_TRAIT);
                    listMemberNameBytes = xmlName != null
                            ? xmlName.getValue().getBytes(StandardCharsets.UTF_8)
                            : MEMBER_BYTES;
                } else {
                    listMemberNameBytes = MEMBER_BYTES;
                }
            }
        }

        // Pre-compute map metadata if target is a map
        boolean mapFlattened = false;
        byte[] mapKeyNameBytes = null;
        byte[] mapValueNameBytes = null;
        byte[] mapEntryNameBytes = null;
        if (target != null && target.type() == ShapeType.MAP) {
            mapFlattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            Schema keySchema = target.mapKeyMember();
            Schema valueSchema = target.mapValueMember();
            if (keySchema != null) {
                var keyXmlName = keySchema.getTrait(TraitKey.XML_NAME_TRAIT);
                mapKeyNameBytes = keyXmlName != null
                        ? keyXmlName.getValue().getBytes(StandardCharsets.UTF_8)
                        : KEY_BYTES;
            } else {
                mapKeyNameBytes = KEY_BYTES;
            }
            if (valueSchema != null) {
                var valueXmlName = valueSchema.getTrait(TraitKey.XML_NAME_TRAIT);
                mapValueNameBytes = valueXmlName != null
                        ? valueXmlName.getValue().getBytes(StandardCharsets.UTF_8)
                        : VALUE_BYTES;
            } else {
                mapValueNameBytes = VALUE_BYTES;
            }
            mapEntryNameBytes = mapFlattened ? null : ENTRY_BYTES;
        }

        // Pre-resolve timestamp format
        TimestampFormatTrait.Format timestampFormat = null;
        var tsFmt = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        if (tsFmt != null) {
            timestampFormat = tsFmt.getFormat();
        }

        return new QueryMemberBinding(
                awsName,
                ec2Name,
                listFlattened,
                listMemberNameBytes,
                mapFlattened,
                mapKeyNameBytes,
                mapValueNameBytes,
                mapEntryNameBytes,
                timestampFormat);
    }

    private static String resolveAwsQueryName(Schema schema) {
        var xmlName = schema.getTrait(TraitKey.XML_NAME_TRAIT);
        return xmlName != null ? xmlName.getValue() : schema.memberName();
    }

    private static String resolveEc2QueryName(Schema schema) {
        var ec2Name = schema.getTrait(TraitKey.get(Ec2QueryNameTrait.class));
        if (ec2Name != null) {
            return ec2Name.getValue();
        }

        var xmlName = schema.getTrait(TraitKey.XML_NAME_TRAIT);
        return xmlName != null
                ? StringUtils.capitalize(xmlName.getValue())
                : StringUtils.capitalize(schema.memberName());
    }

    @SuppressWarnings("deprecation")
    static byte[] encodeName(String name) {
        int len = name.length();

        boolean needsEncoding = false;
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            if (c >= 128 || !QueryUrlEncoding.UNRESERVED[c]) {
                needsEncoding = true;
                break;
            }
        }

        if (!needsEncoding) {
            byte[] result = new byte[len];
            name.getBytes(0, len, result, 0);
            return result;
        }

        // Names needing encoding are rare, and this runs once per schema, so the scratch array is
        // sized for the worst case and the result trimmed rather than measured first.
        byte[] buf = new byte[len * QueryUrlEncoding.MAX_BYTES_PER_CHAR];
        return Arrays.copyOf(buf, QueryUrlEncoding.writeUrlEncoded(buf, 0, name));
    }
}
