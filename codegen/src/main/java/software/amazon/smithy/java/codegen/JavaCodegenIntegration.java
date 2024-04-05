/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import software.amazon.smithy.codegen.core.SmithyIntegration;
import software.amazon.smithy.java.codegen.writer.JavaWriter;

public interface JavaCodegenIntegration
    extends SmithyIntegration<JavaCodegenSettings, JavaWriter, CodeGenerationContext> {}
