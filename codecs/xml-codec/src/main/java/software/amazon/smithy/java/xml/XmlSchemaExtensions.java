/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Pre-computes XML element name bytes and member metadata at schema load time.
 */
@SmithyInternalApi
public final class XmlSchemaExtensions implements SchemaExtensionProvider<XmlSchemaExtensions.XmlExtension> {

    public static final SchemaExtensionKey<XmlExtension> KEY = new SchemaExtensionKey<>();

    @Override
    public SchemaExtensionKey<XmlExtension> key() {
        return KEY;
    }

    @Override
    public XmlExtension provide(Schema schema) {
        var type = schema.type();
        if (schema.isMember()) {
            return provideMember(schema);
        } else if (type == ShapeType.STRUCTURE || type == ShapeType.UNION) {
            return provideStruct(schema);
        } else if (type == ShapeType.LIST) {
            return provideList(schema);
        } else if (type == ShapeType.MAP) {
            return provideMap(schema);
        }
        return null;
    }

    private XmlExtension provideMember(Schema schema) {
        String name = resolveMemberXmlName(schema);
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        boolean isAttribute = schema.hasTrait(TraitKey.XML_ATTRIBUTE_TRAIT);
        boolean isFlattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        byte[] namespaceBytes = null;
        var ns = schema.getDirectTrait(TraitKey.XML_NAMESPACE_TRAIT);
        if (ns != null) {
            namespaceBytes = buildNamespaceBytes(ns.getPrefix().orElse(null), ns.getUri());
        }
        return new MemberExtension(nameBytes, isAttribute, isFlattened, namespaceBytes);
    }

    private XmlExtension provideStruct(Schema schema) {
        var members = schema.members();
        int maxIndex = 0;
        for (var member : members) {
            maxIndex = Math.max(maxIndex, member.memberIndex());
        }

        byte[][] nameTable = new byte[maxIndex + 1][];
        byte[][] nsTable = new byte[maxIndex + 1][];
        boolean[] isAttributeTable = new boolean[maxIndex + 1];
        boolean[] isFlattenedTable = new boolean[maxIndex + 1];
        boolean hasAttributes = false;
        boolean hasNamespaces = false;

        for (var member : members) {
            int idx = member.memberIndex();
            String name = resolveMemberXmlName(member);
            nameTable[idx] = name.getBytes(StandardCharsets.UTF_8);
            isAttributeTable[idx] = member.hasTrait(TraitKey.XML_ATTRIBUTE_TRAIT);
            isFlattenedTable[idx] = member.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            if (isAttributeTable[idx]) {
                hasAttributes = true;
            }
            var ns = member.getDirectTrait(TraitKey.XML_NAMESPACE_TRAIT);
            if (ns != null) {
                nsTable[idx] = buildNamespaceBytes(ns.getPrefix().orElse(null), ns.getUri());
                hasNamespaces = true;
            }
        }

        String structName;
        var xmlNameTrait = schema.getTrait(TraitKey.XML_NAME_TRAIT);
        if (xmlNameTrait != null) {
            structName = xmlNameTrait.getValue();
        } else {
            structName = schema.id().getName();
        }
        byte[] structNameBytes = structName.getBytes(StandardCharsets.UTF_8);

        byte[] namespaceBytes = null;
        var nsTrait = schema.getTrait(TraitKey.XML_NAMESPACE_TRAIT);
        if (nsTrait != null) {
            namespaceBytes = buildNamespaceBytes(nsTrait.getPrefix().orElse(null), nsTrait.getUri());
        }

        return new StructExtension(nameTable,
                hasNamespaces ? nsTable : null,
                isAttributeTable,
                isFlattenedTable,
                structNameBytes,
                namespaceBytes,
                hasAttributes);
    }

    private XmlExtension provideList(Schema schema) {
        boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        String memberName;
        if (flattened) {
            memberName = resolveXmlName(schema);
        } else {
            var member = schema.listMember();
            var memberXmlName = member.getTrait(TraitKey.XML_NAME_TRAIT);
            if (memberXmlName != null) {
                memberName = memberXmlName.getValue();
            } else {
                memberName = "member";
            }
        }
        return new ListExtension(memberName.getBytes(StandardCharsets.UTF_8));
    }

    private XmlExtension provideMap(Schema schema) {
        boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        String xmlName = resolveXmlName(schema);
        String entryName = flattened ? xmlName : "entry";
        String keyName = resolveMapMemberName(schema.mapKeyMember());
        String valueName = resolveMapMemberName(schema.mapValueMember());
        return new MapExtension(
                entryName.getBytes(StandardCharsets.UTF_8),
                keyName.getBytes(StandardCharsets.UTF_8),
                valueName.getBytes(StandardCharsets.UTF_8));
    }

    private static String resolveMemberXmlName(Schema schema) {
        var trait = schema.getDirectTrait(TraitKey.XML_NAME_TRAIT);
        if (trait != null) {
            return trait.getValue();
        } else if (schema.isMember()) {
            return schema.memberName();
        } else {
            return schema.id().getName();
        }
    }

    private static String resolveXmlName(Schema schema) {
        var trait = schema.getDirectTrait(TraitKey.XML_NAME_TRAIT);
        if (trait != null) {
            return trait.getValue();
        } else if (schema.isMember()) {
            return schema.memberName();
        } else {
            return schema.id().getName();
        }
    }

    private static String resolveMapMemberName(Schema schema) {
        var trait = schema.getDirectTrait(TraitKey.XML_NAME_TRAIT);
        if (trait != null) {
            return trait.getValue();
        }
        return schema.memberName();
    }

    private static byte[] buildNamespaceBytes(String prefix, String uri) {
        String escapedUri = uri.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        if (prefix == null || prefix.isEmpty()) {
            return (" xmlns=\"" + escapedUri + "\"").getBytes(StandardCharsets.UTF_8);
        } else {
            return (" xmlns:" + prefix + "=\"" + escapedUri + "\"").getBytes(StandardCharsets.UTF_8);
        }
    }

    public sealed interface XmlExtension permits MemberExtension, StructExtension, ListExtension, MapExtension {}

    public record MemberExtension(
            byte[] nameBytes,
            boolean isAttribute,
            boolean isFlattened,
            byte[] namespaceBytes) implements XmlExtension {}

    public record StructExtension(
            byte[][] nameTable,
            byte[][] memberNamespaceTable,
            boolean[] isAttributeTable,
            boolean[] isFlattenedTable,
            byte[] structNameBytes,
            byte[] namespaceBytes,
            boolean hasAttributes) implements XmlExtension {}

    public record ListExtension(
            byte[] memberNameBytes) implements XmlExtension {}

    public record MapExtension(
            byte[] entryNameBytes,
            byte[] keyNameBytes,
            byte[] valueNameBytes) implements XmlExtension {}
}
