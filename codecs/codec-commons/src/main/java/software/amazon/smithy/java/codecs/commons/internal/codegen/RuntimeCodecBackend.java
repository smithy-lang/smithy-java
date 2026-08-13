/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

/**
 * Generation-time backend for a wire codec.
 *
 * <p>This type is internal. Generated operation methods must not retain or call
 * a backend or a {@link RuntimeCodecPlan}.
 *
 * @param <T> generated codec interface
 */
public interface RuntimeCodecBackend<T> {
    String id();

    Class<T> codecType();

    Class<?> lookupHost();

    Emission emit(RuntimeCodecPlan plan, String generatedName);

    record Emission(byte[] bytecode, int methodCount) {}
}
