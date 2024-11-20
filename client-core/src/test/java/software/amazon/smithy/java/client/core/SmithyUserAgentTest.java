/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import java.util.List;
import org.junit.jupiter.api.Test;

public class SmithyUserAgentTest {
    @Test
    public void createsDefaultHeader() {
        var ua = SmithyUserAgent.create();

        assertThat(ua.toString(), startsWith("smithy-java/"));
        assertThat(ua.toString(), not(endsWith(" ")));
    }

    @Test
    public void appendsEntries() {
        var ua = SmithyUserAgent.create();
        ua.addEntry("md", "hello");
        ua.addEntry("md", "goodbye");

        assertThat(ua.toString(), containsString("md/hello md/goodbye"));
    }

    @Test
    public void appendToExistingEntry() {
        var ua = SmithyUserAgent.create();
        ua.appendToEntry("md", "hello");
        ua.appendToEntry("md", "goodbye");

        assertThat(ua.toString(), containsString("md/hello,goodbye"));
    }

    @Test
    public void appendToLastExistingEntry() {
        var ua = SmithyUserAgent.create();
        ua.addEntry("md", "hi1");
        ua.addEntry("md", "hi2");
        ua.appendToEntry("md", "hello");

        assertThat(ua.toString(), containsString("md/hi1 md/hi2,hello"));
    }

    @Test
    public void sanitizesEntries() {
        var ua = SmithyUserAgent.create();
        ua.appendToEntry("md", "h/2");

        assertThat(ua.toString(), containsString("md/h_2"));
    }

    @Test
    public void setsExecutionEnvironment() {
        var ua = SmithyUserAgent.create();
        ua.setExecutionEnv("lambda");

        assertThat(ua.toString(), containsString("exec-env/lambda"));
    }

    @Test
    public void canReplaceEntries() {
        var ua = SmithyUserAgent.create();
        ua.putEntry("foo", "bar");
        ua.putEntry("foo", "baz");

        assertThat(ua.toString(), containsString("foo/baz"));
        assertThat(ua.toString(), not(containsString("foo/bar")));
    }

    @Test
    public void canSetTool() {
        var ua = SmithyUserAgent.create();
        ua.setTool("mike/123", "1.0");

        assertThat(ua.toString(), startsWith("mike_123/1.0 smithy-java/"));
    }

    @Test
    public void canInspectValues() {
        var ua = SmithyUserAgent.create();
        ua.putEntry("foo", "bar");

        assertThat(ua.getEntryNames(), contains("foo"));
        assertThat(ua.getFirstEntry("foo"), equalTo("bar"));
        assertThat(ua.getEntry("foo"), equalTo(List.of("bar")));
    }
}
