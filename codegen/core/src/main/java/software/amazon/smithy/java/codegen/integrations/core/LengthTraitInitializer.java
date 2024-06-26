/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.LengthTrait;

final class LengthTraitInitializer implements TraitInitializer<LengthTrait> {

    @Override
    public Class<LengthTrait> traitClass() {
        return LengthTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, LengthTrait lengthTrait) {
        writer.pushState();
        writer.putContext("min", lengthTrait.getMin());
        writer.putContext("max", lengthTrait.getMax());
        writer.putContext("length", LengthTrait.class);
        writer.writeInline("${length:T}.builder()${?min}.min(${min:L}L)${/min}${?max}.max(${max:L}L)${/max}.build()");
        writer.popState();
    }
}
