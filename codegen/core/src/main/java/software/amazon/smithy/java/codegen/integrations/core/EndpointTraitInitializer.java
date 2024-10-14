/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.EndpointTrait;

final class EndpointTraitInitializer implements TraitInitializer<EndpointTrait> {

    @Override
    public Class<EndpointTrait> traitClass() {
        return EndpointTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, EndpointTrait endpointTrait) {
        writer.putContext("endpoint", EndpointTrait.class);
        writer.putContext("hostPrefix", endpointTrait.getHostPrefix());
        writer.writeInline("${endpoint:T}.builder().hostPrefix(${hostPrefix:S}).build()");
    }
}
