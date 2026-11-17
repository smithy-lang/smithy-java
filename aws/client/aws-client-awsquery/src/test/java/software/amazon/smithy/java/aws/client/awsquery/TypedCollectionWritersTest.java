/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codecs.commons.OutputBuffers;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture;
import software.amazon.smithy.java.core.serde.testing.CollectionWriterFixture.Options;

public class TypedCollectionWritersTest {

    private static String serialize(QueryFormSerializer.QueryVariant variant, SerializableStruct struct) {
        var serializer = QueryFormSerializer.acquire(variant, "Action", "2020-01-01", OutputBuffers.shared());
        serializer.writeStruct(struct.schema(), struct);
        return StandardCharsets.UTF_8.decode(serializer.finish()).toString();
    }

    @Test
    public void awsQueryTypedWritersMatchGenericWriters() {
        var variant = QueryFormSerializer.QueryVariant.AWS_QUERY;
        var typed = serialize(variant, CollectionWriterFixture.typed(Options.NO_DOCUMENTS));
        var generic = serialize(variant, CollectionWriterFixture.generic(Options.NO_DOCUMENTS));

        assertThat(typed, equalTo(generic));
        assertThat(typed, containsString("&stringList.member.1=a&stringList.member.2=b%20c"));
        assertThat(typed, containsString("&sparseIntegerMap.entry.1.key=k1&sparseIntegerMap.entry.1.value=5"));
        assertThat(typed, containsString("&nestedMap.entry.2.key=k2&nestedMap.entry.2.value.member.1=d"));
        assertThat(typed, containsString("&nestedList.member.1.member.member.2=b&nestedList.member.2.member="));
    }

    @Test
    public void ec2QueryTypedWritersMatchGenericWriters() {
        var variant = QueryFormSerializer.QueryVariant.EC2_QUERY;
        var typed = serialize(variant, CollectionWriterFixture.typed(Options.LISTS_ONLY));
        var generic = serialize(variant, CollectionWriterFixture.generic(Options.LISTS_ONLY));

        assertThat(typed, equalTo(generic));
        assertThat(typed, containsString("&StringList.1=a&StringList.2=b%20c"));
        assertThat(typed, containsString("&NestedList.1.Member.1=a&NestedList.1.Member.2=b"));
    }
}
