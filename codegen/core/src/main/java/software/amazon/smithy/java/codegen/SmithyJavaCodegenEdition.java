/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

/**
 * The edition of the Smithy-Java code generator to use for code generation.
 *
 * <p>Editions can automatically enable and disable feature-gates in the generator.
 * For example, if a new way to generate unions is added to the code generator,
 * the generator can continue support the existing union behavior, add a feature
 * gate to generate the new union code, and eventually add a new edition that enables this feature by default.
 *
 * @see <a href="https://smithy.io/2.0/guides/building-codegen/configuring-the-generator.html#edition">Smithy code generator editions</a>
 */
public enum SmithyJavaCodegenEdition {
    V2024,
    LATEST
}
