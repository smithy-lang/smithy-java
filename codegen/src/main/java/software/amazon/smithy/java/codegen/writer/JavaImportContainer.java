/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.writer;

import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;

record JavaImportContainer(JavaCodegenSettings settings, String namespace) implements ImportContainer {
    @Override
    public void importSymbol(Symbol symbol, String s) {

    }
}
