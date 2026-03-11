/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.traits.StringListTrait;

final class StringListTraitInitializer implements TraitInitializer<StringListTrait> {
    @Override
    public Class<StringListTrait> traitClass() {
        return StringListTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, StringListTrait stringListTrait) {
        writer.writeInline(
                "new $T($S, $T.NONE)",
                stringListTrait.getClass(),
                stringListTrait.getValues(),
                SourceLocation.class);
    }
}
