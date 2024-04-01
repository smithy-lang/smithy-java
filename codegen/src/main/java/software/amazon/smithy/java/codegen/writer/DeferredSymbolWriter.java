/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.writer;

import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.SymbolWriter;

public abstract class DeferredSymbolWriter<W extends SymbolWriter<W, I>, I extends ImportContainer> extends
        SymbolWriter<W, I> {

    public DeferredSymbolWriter(I importContainer) {
        super(importContainer);
    }
}
