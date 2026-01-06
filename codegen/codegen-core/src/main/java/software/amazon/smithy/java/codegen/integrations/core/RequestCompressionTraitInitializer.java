/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import java.util.List;
import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.RequestCompressionTrait;

final class RequestCompressionTraitInitializer implements TraitInitializer<RequestCompressionTrait> {
    @Override
    public Class<RequestCompressionTrait> traitClass() {
        return RequestCompressionTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, RequestCompressionTrait requestCompressionTrait) {
        writer.putContext("enc", requestCompressionTrait.getEncodings());
        writer.putContext("requestComp", RequestCompressionTrait.class);
        writer.putContext("list", List.class);
        writer.writeInline(
                "${requestComp:T}.builder().encodings(${list:T}.of(${#enc}${value:S}${^key.last}, ${/key.last}${/enc})).build()");
    }
}
