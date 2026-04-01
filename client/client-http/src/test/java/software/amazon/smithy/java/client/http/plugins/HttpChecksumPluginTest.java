/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;

public class HttpChecksumPluginTest {

    @Test
    public void interceptorAddsContentMd5HeaderForKnownBody() throws Exception {
        var interceptor = new HttpChecksumPlugin.HttpChecksumInterceptor();
        var req = HttpRequest.create()
                .setUri(new URI("/"))
                .setMethod("POST")
                .setBody(DataStream.ofBytes("test body".getBytes(StandardCharsets.UTF_8)))
                .toUnmodifiable();

        var result = interceptor.addContentMd5Header(req);

        var headers = result.headers().allValues("Content-MD5");
        assertThat(headers, hasSize(1));
        assertThat(headers.get(0), equalTo("u/mv50Mcr1+Jpgi8MejYIg=="));
    }

    @Test
    public void interceptorReplacesExistingContentMd5Header() throws Exception {
        var interceptor = new HttpChecksumPlugin.HttpChecksumInterceptor();
        var req = HttpRequest.create()
                .setUri(new URI("/"))
                .setMethod("POST")
                .setBody(DataStream.ofBytes("test body".getBytes(StandardCharsets.UTF_8)))
                .addHeader("Content-MD5", "wrong-hash")
                .toUnmodifiable();

        var result = interceptor.addContentMd5Header(req);

        var headers = result.headers().allValues("Content-MD5");
        assertThat(headers, hasSize(1));
        assertThat(headers.get(0), equalTo("u/mv50Mcr1+Jpgi8MejYIg=="));
    }

}
