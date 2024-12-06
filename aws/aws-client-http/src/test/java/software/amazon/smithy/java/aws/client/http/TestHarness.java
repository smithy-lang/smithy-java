/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.http;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

final class TestHarness {

    static final Model MODEL = Model.assembler()
        .addUnparsedModel("test.smithy", """
            $version: "2"
            namespace smithy.example

            service Sprockets {
                operations: [CreateSprocket]
            }

            operation CreateSprocket {
                input := {}
                output := {}
            }
            """)
        .assemble()
        .unwrap();

    static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");
}
