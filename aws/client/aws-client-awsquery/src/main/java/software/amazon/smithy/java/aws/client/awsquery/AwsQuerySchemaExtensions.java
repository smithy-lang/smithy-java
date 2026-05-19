/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.nio.ByteBuffer;
import software.amazon.smithy.aws.traits.protocols.Ec2QueryNameTrait;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Pre-computes the URL-encoded member-name bytes for AWS Query and EC2 Query protocols once per {@link Schema}.
 */
@SmithyInternalApi
public final class AwsQuerySchemaExtensions
        implements SchemaExtensionProvider<AwsQuerySchemaExtensions.QueryMemberBinding> {

    public static final SchemaExtensionKey<QueryMemberBinding> KEY = new SchemaExtensionKey<>();

    /**
     * Pre-encoded member-name bytes for both query variants.
     *
     * @param awsQueryNameBytes Bytes to use as the awsQuery name. Never null.
     * @param ec2QueryNameBytes Bytes to use as the ec2Query name. Never null.
     */
    public record QueryMemberBinding(byte[] awsQueryNameBytes, byte[] ec2QueryNameBytes) {}

    @Override
    public SchemaExtensionKey<QueryMemberBinding> key() {
        return KEY;
    }

    @Override
    public QueryMemberBinding provide(Schema schema) {
        if (!schema.isMember()) {
            return null;
        }

        return new QueryMemberBinding(
                encodeName(resolveAwsQueryName(schema)),
                encodeName(resolveEc2QueryName(schema)));
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
            if (!FormUrlEncodedSink.isUnreserved(c)) {
                needsEncoding = true;
                break;
            }
        }

        if (!needsEncoding) {
            byte[] result = new byte[len];
            name.getBytes(0, len, result, 0);
            return result;
        }

        FormUrlEncodedSink tmp = new FormUrlEncodedSink(len * 3);
        tmp.writeUrlEncoded(name);
        ByteBuffer bb = tmp.finish();
        byte[] result = new byte[bb.remaining()];
        bb.get(result);
        return result;
    }
}
