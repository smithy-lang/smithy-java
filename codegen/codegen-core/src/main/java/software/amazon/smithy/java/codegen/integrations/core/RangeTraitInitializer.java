/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import java.math.BigDecimal;
import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.RangeTrait;

final class RangeTraitInitializer implements TraitInitializer<RangeTrait> {
    @Override
    public Class<RangeTrait> traitClass() {
        return RangeTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, RangeTrait rangeTrait) {
        writer.putContext("min", rangeTrait.getMin());
        writer.putContext("max", rangeTrait.getMax());
        writer.putContext("range", RangeTrait.class);
        writer.putContext("bigDecimal", BigDecimal.class);
        writer.writeInline(
                "${range:T}.builder()${?min}.min(new ${bigDecimal:T}(${min:S}))${/min}"
                        + "${?max}.max(new ${bigDecimal:T}(${max:S}))${/max}.build()");
    }
}
