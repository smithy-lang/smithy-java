/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.util.List;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;

abstract class ProviderTestBase {

    static final boolean STAX = false;
    static final boolean NATIVE = true;

    static List<Arguments> providers() {
        return List.of(
                Arguments.of(Named.of("stax", STAX)),
                Arguments.of(Named.of("native", NATIVE)));
    }

    static List<Arguments> crossProviders() {
        return List.of(
                Arguments.of(Named.of("stax->stax", STAX), STAX),
                Arguments.of(Named.of("native->native", NATIVE), NATIVE),
                Arguments.of(Named.of("stax->native", STAX), NATIVE),
                Arguments.of(Named.of("native->stax", NATIVE), STAX));
    }

    static XmlCodec codec(boolean useNative) {
        return XmlCodec.builder().useNative(useNative).build();
    }
}
