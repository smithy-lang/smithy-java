/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JavaHttpClientTest {

    private static final String NAME = "jdk.httpclient.allowRestrictedHeaders";
    private static String originalValue;

    @BeforeAll
    public static void init() {
        originalValue = System.getProperty(NAME, "");
    }

    @AfterAll
    public static void cleanup() {
        System.setProperty(NAME, originalValue);
    }

    @Test
    public void setsHostInAllowedHeaders() {
        System.setProperty(NAME, "");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("host"));
    }

    @Test
    public void setsHostInAllowedHeadersWhenOtherValuesPresent() {
        System.setProperty(NAME, "foo");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("foo,host"));
    }

    @Test
    public void doesNotSetHostWhenIsolated() {
        System.setProperty(NAME, "host");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("host"));
    }

    @Test
    public void doesNotSetHostWhenTrailing() {
        System.setProperty(NAME, "foo,host");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("foo,host"));
    }

    @Test
    public void doesNotSetHostWhenLeading() {
        System.setProperty(NAME, "Host,foo");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("Host,foo"));
    }
}
