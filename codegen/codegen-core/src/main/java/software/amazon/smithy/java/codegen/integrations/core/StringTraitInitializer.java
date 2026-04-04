/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.StringTrait;

final class StringTraitInitializer implements TraitInitializer<StringTrait> {
    @Override
    public Class<StringTrait> traitClass() {
        return StringTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, StringTrait stringTrait) {
        var stringValue = stringTrait.getValue().replace("£", "££");
        writer.writeInline("new $T($S)", stringTrait.getClass(), stringValue);
    }
}
