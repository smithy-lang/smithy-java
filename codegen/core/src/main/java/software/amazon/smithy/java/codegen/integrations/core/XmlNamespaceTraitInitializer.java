/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

final class XmlNamespaceTraitInitializer implements TraitInitializer<XmlNamespaceTrait> {
    @Override
    public Class<XmlNamespaceTrait> traitClass() {
        return XmlNamespaceTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, XmlNamespaceTrait xmlNamespaceTrait) {
        writer.pushState();
        writer.putContext("xmlNamespace", XmlNamespaceTrait.class);
        writer.putContext("uri", xmlNamespaceTrait.getUri());
        writer.putContext("prefix", xmlNamespaceTrait.getPrefix());
        writer.writeInline("${xmlNamespace:T}.builder().uri(${uri:S})${?prefix}.prefix(${prefix:S})${/prefix}.build()");
        writer.popState();
    }
}
