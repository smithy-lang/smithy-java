/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class DefaultTraitInitializer implements TraitInitializer<DefaultTrait> {

    @Override
    public Class<DefaultTrait> traitClass() {
        return DefaultTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, DefaultTrait defaultTrait) {
        writer.pushState();
        writer.putContext("default", DefaultTrait.class);
        writer.putContext("nodeInitializer", new NodeWriter(writer, defaultTrait.toNode()));
        writer.writeInline("new ${default:T}(${nodeInitializer:C})");
        writer.popState();
    }
}
