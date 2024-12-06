/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.framework;

import java.util.Map;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.java.framework.model.AccessDeniedException;
import software.amazon.smithy.java.framework.model.InternalFailureException;
import software.amazon.smithy.java.framework.model.MalformedRequestException;
import software.amazon.smithy.java.framework.model.NotAuthorizedException;
import software.amazon.smithy.java.framework.model.ThrottlingException;
import software.amazon.smithy.java.framework.model.UnknownOperationException;
import software.amazon.smithy.java.framework.model.ValidationException;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class FrameworkErrorIntegration implements JavaCodegenIntegration {
    @Override
    public String name() {
        return "framework-errors";
    }

    @Override
    public byte priority() {
        // Run before any other integration so customers can override mappings.
        return 127;
    }

    @Override
    public Map<ShapeId, Class<? extends ModeledApiException>> implicitErrorMappings() {
        return Map.of(
            AccessDeniedException.$ID,
            AccessDeniedException.class,
            InternalFailureException.$ID,
            InternalFailureException.class,
            MalformedRequestException.$ID,
            MalformedRequestException.class,
            NotAuthorizedException.$ID,
            NotAuthorizedException.class,
            ThrottlingException.$ID,
            ThrottlingException.class,
            UnknownOperationException.$ID,
            UnknownOperationException.class,
            ValidationException.$ID,
            ValidationException.class
        );
    }
}
