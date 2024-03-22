/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.uri;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.uri.PathBuilder;
import software.amazon.smithy.java.runtime.core.uri.URIBuilder;

public class URIBuilderTest {
    @Test
    public void buildsUri() {

        String path = new PathBuilder().addSegment("foo bar")
                .addSegment("baz+bam")
                .addSegment("-._~")
                .addSegment("*")
                .addSegment("%%")
                .build();

        URI uri = new URIBuilder().scheme("https")
                .host("example.com")
                .path(path)
                .query("how&does=it&do=it%20I%20wonder?")
                .build();

        assertThat(uri.toString(),
                equalTo("https://example.com/foo%20bar/baz%2Bbam/-._~/%2A/%25%25?how&does=it&do=it%20I%20wonder?"));
        assertThat(uri.getScheme(), equalTo("https"));
        assertThat(uri.getHost(), equalTo("example.com"));
        assertThat(uri.getRawPath(), equalTo("/foo%20bar/baz%2Bbam/-._~/%2A/%25%25"));
        assertThat(uri.getPath(), equalTo("/foo bar/baz+bam/-._~/*/%%"));
        assertThat(uri.getRawQuery(), equalTo("how&does=it&do=it%20I%20wonder?"));

        assertThat(URIBuilder.of(uri).build(), Matchers.equalTo(uri));
    }
}
