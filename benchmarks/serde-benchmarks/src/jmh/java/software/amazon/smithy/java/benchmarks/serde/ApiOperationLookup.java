/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Resolves the codegen-generated {@link ApiOperation} singleton for a given
 * operation name within a protocol-specific generated package.
 *
 * <p>The {@code java-codegen} plugin in {@code client} mode emits each
 * operation as {@code <generatedPackage>.<OperationName>} with a public
 * static {@code instance()} factory.
 */
final class ApiOperationLookup {

    private ApiOperationLookup() {}

    @SuppressWarnings("unchecked")
    static ApiOperation<? extends SerializableStruct,
            ? extends SerializableStruct> resolve(String generatedPackage, String operationName) {
        String fqcn = generatedPackage + "." + operationName;
        try {
            Class<?> opClass = Class.forName(fqcn);
            Method instanceMethod = opClass.getMethod("instance");
            return (ApiOperation<? extends SerializableStruct, ? extends SerializableStruct>) instanceMethod
                    .invoke(null);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "No generated ApiOperation class found at " + fqcn
                            + ". Has codegen been re-run for this protocol?",
                    e);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    "Failed to obtain instance of generated ApiOperation " + fqcn,
                    e);
        }
    }
}
