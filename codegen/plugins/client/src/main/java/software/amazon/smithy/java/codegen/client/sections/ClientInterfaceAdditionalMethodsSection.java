/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.sections;

import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.utils.CodeSection;

/**
 * This section provides a hook for adding additional methods to the generated client interface.
 *
 * @param client Client shape that these methods will be added to.
 * @param async true if the client is an async client.
 */
public record ClientInterfaceAdditionalMethodsSection(ServiceShape client, boolean async) implements CodeSection {}
