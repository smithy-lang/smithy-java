/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.example;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.aws.traits.ArnTrait;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;

/**
 * Determine and log arn and operation scoped name.
 *
 * <p>To run this as a service interceptor and could have it call an AuthZ system.
 * Ideally the Service name could be pulled and cached from the service
 * schema attached to an operation.
 */
record ArnMapper(String serviceName, String region) {
    public <I extends SerializableStruct> String getActionName(I input, ApiOperation<I, ?> operation) {
        var resourceSchema = operation.schema().resource();
        return resourceSchema.id().getName() + "::" + operation.schema().id().getName();
    }

    public <I extends SerializableStruct> String getResourceArn(I input, ApiOperation<I, ?> operation) {
        // Get arn values
        var resourceSchema = operation.schema().resource();
        var arnTrait = resourceSchema.getTrait(TraitKey.get(ArnTrait.class));
        List<String> replacements = new ArrayList<>(arnTrait.getLabels().size());
        var labels = arnTrait.getLabels();
        for (var label : labels) {
            var identifier = resourceSchema.identifiers().get(label);
            if (identifier == null) {
                throw new IllegalArgumentException("Could not find expected identifier");
            }
            // TODO: Either get by name or get by `@resourceIdentifier` trait
            var schema = input.schema().member(identifier.memberName());
            var value = input.getMemberValue(schema);
            if (value == null) {
                throw new IllegalArgumentException("Could not find expected value");
            }
            replacements.add(value.toString());
        }
        // Create arn from template
        var template = arnTrait.getTemplate();
        for (int i = 0; i < labels.size(); i++) {
            template = template.replace("{" + labels.get(i) + "}", replacements.get(i));
        }
        // Partition is just placeholder for example.
        // An arbitrary account ID is added but could be extracted from Identity info in job.
        var accountId = "123456787";
        return "arn:example:" + serviceName + ":" + region + ":" + accountId + ":" + template;
    }
}
