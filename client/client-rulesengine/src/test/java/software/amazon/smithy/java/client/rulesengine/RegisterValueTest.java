/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

public class RegisterValueTest {
    @Test
    public void storesSingleValue() {
        var definition = new RegisterDefinition("a");
        var value = new RegisterValue(definition);

        assertThat(value.get(), nullValue());
        value.push("foo");
        assertThat(value.get(), equalTo("foo"));
    }

    @Test
    public void pushesAndPops() {
        var definition = new RegisterDefinition("a");
        var value = new RegisterValue(definition);

        assertThat(value.get(), nullValue());
        value.push("foo");
        value.push("bar");
        value.push("baz");

        assertThat(value.get(), equalTo("baz"));

        value.pop();
        assertThat(value.get(), equalTo("bar"));

        value.pop();
        assertThat(value.get(), equalTo("foo"));

        value.pop();
        assertThat(value.get(), nullValue());
    }

    @Test
    public void tooManyPopsIgnored() {
        var definition = new RegisterDefinition("a");
        var value = new RegisterValue(definition);

        value.pop();
        value.pop();
        value.pop();
    }
}
