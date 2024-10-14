/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait;

final class HttpApiKeyAuthTraitInitializer implements TraitInitializer<HttpApiKeyAuthTrait> {
    @Override
    public Class<HttpApiKeyAuthTrait> traitClass() {
        return HttpApiKeyAuthTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, HttpApiKeyAuthTrait httpApiKeyAuthTrait) {
        writer.putContext("auth", HttpApiKeyAuthTrait.class);
        writer.putContext("name", httpApiKeyAuthTrait.getName());
        writer.putContext("in", httpApiKeyAuthTrait.getIn());
        writer.putContext("scheme", httpApiKeyAuthTrait.getScheme());
        writer.writeInline("""
            ${auth:T}.builder()
                .name(${name:S})
                .in(${auth:T}.Location.from(${in:S})${?scheme}
                .scheme(${scheme:S})${/scheme}
                .build()""");
    }
}
