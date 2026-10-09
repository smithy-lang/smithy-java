/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.model.node.Node;

/**
 * Baseline shim: in this SDK (Feb 2026) {@code ProtocolTestDocument} is package-private, so the e2e harness
 * cannot reference it directly as it does against the current SDK where the type is public. This class lives in
 * the same package and is compiled into the benchmark jar, exposing the one call the harness needs: turn a
 * protocol-test {@code params} node into a typed input with protocol-test semantics. Not part of any public API.
 */
public final class ProtocolTestDocumentBridge {

    private ProtocolTestDocumentBridge() {}

    public static <T extends SerializableShape> void deserializeInto(
            Node params,
            String contentType,
            ShapeBuilder<T> builder
    ) {
        new ProtocolTestDocument(params, contentType).deserializeInto(builder);
    }
}
