/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

/**
 * The opening and closing runs of a document's root element.
 *
 * <p>Generated codecs take these as arguments instead of baking them, because the root element name is
 * a property of the schema the caller hands over, not of the shape being written. An
 * {@code @httpPayload} member carrying {@code @xmlName} renames the element around a structure that
 * would otherwise use its own name, so a codec generated per shape class cannot know it.
 *
 * <p>Resolution here mirrors {@code SmithyXmlSerializer.resolveTopLevelName} and
 * {@code resolveTopLevelNamespace} exactly, including their use of the inherited {@code getTrait}
 * rather than {@code getDirectTrait}. {@link #open} deliberately stops short of the {@code '>'}: a root
 * structure with attributes needs them written between the namespace and the {@code '>'}, so only the
 * generated code knows where that byte goes.
 *
 * @param open {@code '<'}, the element name, and any namespace declaration.
 * @param close {@code "</name>"}.
 */
record XmlRootTags(byte[] open, byte[] close) {

    static XmlRootTags of(Schema schema, XmlSettings settings) {
        String name = name(schema);
        return new XmlRootTags(
                ("<" + name + namespace(schema, settings)).getBytes(StandardCharsets.UTF_8),
                ("</" + name + ">").getBytes(StandardCharsets.UTF_8));
    }

    private static String name(Schema schema) {
        var trait = schema.getTrait(TraitKey.XML_NAME_TRAIT);
        if (trait != null) {
            return trait.getValue();
        }
        var extension = structExtension(schema);
        if (extension != null) {
            return new String(extension.structNameBytes(), StandardCharsets.UTF_8);
        }
        return schema.isMember() ? schema.memberTarget().id().getName() : schema.id().getName();
    }

    private static String namespace(Schema schema, XmlSettings settings) {
        var extension = structExtension(schema);
        if (extension != null && extension.namespaceBytes() != null) {
            return new String(extension.namespaceBytes(), StandardCharsets.UTF_8);
        }
        XmlNamespaceTrait namespace = schema.getTrait(TraitKey.XML_NAMESPACE_TRAIT);
        if (namespace == null) {
            namespace = settings.defaultNamespace();
        }
        return namespace == null ? "" : namespaceRun(namespace);
    }

    private static XmlSchemaExtensions.StructExtension structExtension(Schema schema) {
        Schema target = schema.isMember() ? schema.memberTarget() : schema;
        var extension = target.getExtension(XmlSchemaExtensions.KEY);
        return extension instanceof XmlSchemaExtensions.StructExtension se ? se : null;
    }

    /** Mirrors {@code SmithyXmlSerializer.buildNamespaceBytes}, escapes included. */
    static String namespaceRun(XmlNamespaceTrait namespace) {
        String prefix = namespace.getPrefix().orElse(null);
        String escapedUri = namespace.getUri()
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        if (prefix == null || prefix.isEmpty()) {
            return " xmlns=\"" + escapedUri + "\"";
        }
        return " xmlns:" + prefix + "=\"" + escapedUri + "\"";
    }
}
