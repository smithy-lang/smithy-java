/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture.Options;

public class TypedCollectionWritersTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void typedWritersMatchGenericWriters(boolean useNative) {
        try (var codec = XmlCodec.builder().useNative(useNative).build()) {
            var typed = codec.serializeToString(CollectionWriterFixture.typed(Options.NO_DOCUMENTS));
            var generic = codec.serializeToString(CollectionWriterFixture.generic(Options.NO_DOCUMENTS));

            assertThat(typed, equalTo(generic));
            assertThat(typed, containsString("<stringList><member>a</member><member>b c</member></stringList>"));
            assertThat(typed, containsString("<sparseIntegerMap><entry><key>k1</key><value>5</value></entry>"));
            assertThat(typed,
                    containsString("<nestedMap><entry><key>k1</key><value><member>c</member></value></entry>"));
        }
    }
}
