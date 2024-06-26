/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.RetryableTrait;

final class RetryableTraitInitializer implements TraitInitializer<RetryableTrait> {
    @Override
    public Class<RetryableTrait> traitClass() {
        return RetryableTrait.class;
    }

    @Override
    public void accept(JavaWriter writer, RetryableTrait retryableTrait) {
        writer.pushState();
        writer.putContext("retryable", RetryableTrait.class);
        writer.putContext("throttling", retryableTrait.getThrottling());
        writer.writeInline("${retryable:T}.builder().throttling(${throttling:L}).build()");
        writer.popState();
    }
}
