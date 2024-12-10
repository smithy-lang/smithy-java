/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.HttpErrorTrait;

final class HttpErrorTraitInitializer implements TraitInitializer<HttpErrorTrait> {
    @Override
    public Class<HttpErrorTrait> traitClass() {
        return HttpErrorTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, HttpErrorTrait httpTrait) {
        writer.putContext("httpError", HttpErrorTrait.class);
        writer.putContext("code", httpTrait.getCode());
        writer.writeInline("new ${httpError:T}(${code:L})");
    }
}
