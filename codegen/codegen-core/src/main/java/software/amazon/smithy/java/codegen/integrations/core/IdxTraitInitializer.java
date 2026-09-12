/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.IdxTrait;

final class IdxTraitInitializer implements TraitInitializer<IdxTrait> {
    @Override
    public Class<IdxTrait> traitClass() {
        return IdxTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, IdxTrait idxTrait) {
        writer.putContext("idx", IdxTrait.class);
        writer.writeInline("new ${idx:T}($L)", idxTrait.getValue());
    }
}
