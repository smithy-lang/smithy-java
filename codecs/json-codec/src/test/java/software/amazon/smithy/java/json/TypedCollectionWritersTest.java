/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture.Options;

public class TypedCollectionWritersTest extends ProviderTestBase {

    @ParameterizedTest
    @MethodSource("providers")
    public void typedWritersMatchGenericWriters(JsonSerdeProvider provider) {
        try (var codec = codec(provider)) {
            var typed = codec.serializeToString(CollectionWriterFixture.typed(Options.ALL));
            var generic = codec.serializeToString(CollectionWriterFixture.generic(Options.ALL));

            assertThat(typed, equalTo(generic));
            assertThat(typed, containsString("\"stringList\":[\"a\",\"b c\"]"));
            assertThat(typed, containsString("\"sparseIntegerMap\":{\"k1\":5,\"k2\":null}"));
            assertThat(typed, containsString("\"nestedMap\":{\"k1\":[\"c\"],\"k2\":[\"d\",\"e\"]}"));
        }
    }
}
