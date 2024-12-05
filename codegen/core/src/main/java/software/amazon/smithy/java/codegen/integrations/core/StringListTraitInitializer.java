/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import java.util.List;
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
        writer.pushState();
        writer.putContext("trait", stringListTrait.getClass());
        writer.putContext("values", stringListTrait.getValues());
        writer.putContext("location", SourceLocation.class);
        writer.putContext("list", List.class);
        writer.writeInline(
            "new ${trait:T}(${list:T}.of(${#values}${value:S}${^key.last}, ${/key.last}${/values}), ${location:T}.NONE)"
        );
        writer.popState();
    }
}
