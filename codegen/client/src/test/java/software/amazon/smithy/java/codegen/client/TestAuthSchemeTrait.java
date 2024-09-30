/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;

public class TestAuthSchemeTrait extends AnnotationTrait {
    public static final ShapeId ID = ShapeId.from("smithy.test.auth#testAuthScheme");

    public TestAuthSchemeTrait() {
        this(Node.objectNode());
    }

    public TestAuthSchemeTrait(ObjectNode node) {
        super(ID, node);
    }

    public static final class Provider extends AnnotationTrait.Provider<TestAuthSchemeTrait> {
        public Provider() {
            super(TestAuthSchemeTrait.ID, TestAuthSchemeTrait::new);
        }
    }
}
