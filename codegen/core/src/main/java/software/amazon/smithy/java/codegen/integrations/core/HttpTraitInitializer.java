/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.traits.HttpTrait;

final class HttpTraitInitializer implements TraitInitializer<HttpTrait> {
    @Override
    public Class<HttpTrait> traitClass() {
        return HttpTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, HttpTrait httpTrait) {
        writer.putContext("http", HttpTrait.class);
        writer.putContext("method", httpTrait.getMethod());
        writer.putContext("code", httpTrait.getCode());
        writer.putContext("uriPattern", UriPattern.class);
        writer.putContext("uri", httpTrait.getUri());
        writer.writeInline(
            "${http:T}.builder().method(${method:S}).code(${code:L}).uri(${uriPattern:T}.parse(${uri:S})).build()"
        );
    }
}
