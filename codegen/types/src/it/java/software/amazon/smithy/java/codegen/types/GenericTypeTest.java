/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import org.junit.jupiter.api.Test;
import smithy.java.codegen.types.test.model.Beer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

public class GenericTypeTest {

    @Test
    public void testGenericType() {
        var beer = Beer.builder().id(1L).name("lager").build();
        var document = Document.createTyped(beer);
        document.deserializeInto(Beer.builder());
    }
}
