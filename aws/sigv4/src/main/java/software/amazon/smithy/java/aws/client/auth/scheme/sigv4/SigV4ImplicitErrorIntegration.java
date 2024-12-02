/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.util.Map;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.model.InvalidSignatureException;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.model.shapes.ShapeId;

public class SigV4ImplicitErrorIntegration implements JavaCodegenIntegration {
    @Override
    public Map<ShapeId, Class<? extends ModeledApiException>> implicitErrorMappings() {
        return Map.of(InvalidSignatureException.$ID, InvalidSignatureException.class);
    }
}
