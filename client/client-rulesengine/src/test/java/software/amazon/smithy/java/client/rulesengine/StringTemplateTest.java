/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.rulesengine.language.syntax.expressions.Template;

public class StringTemplateTest {
    @Test
    public void createsFromSingularExpression() {
        var template = Template.fromString("{Region}");
        var st = StringTemplate.from(template);
        List<Object> calls = new ArrayList<>();

        assertThat(st.expressionCount(), is(1));
        assertThat(st.singularExpression(), notNullValue());
        assertThat(st.resolve(1, new Object[] {"test"}), equalTo("test"));

        st.forEachExpression(calls::add);
        assertThat(calls, hasSize(1));
    }

    @Test
    public void stEquality() {
        var template = Template.fromString("foo/{Region}");
        var st1 = StringTemplate.from(template);
        var st2 = StringTemplate.from(template);
        var st3 = StringTemplate.from(Template.fromString("bar/{Region}"));

        assertThat(st1, equalTo(st1));
        assertThat(st2, equalTo(st1));
        assertThat(st3, not(equalTo(st1)));
    }

    @Test
    public void loadsTemplatesWithMixedParts() {
        var template = Template.fromString("https://foo.{Region}.{Other}.com");
        var st = StringTemplate.from(template);
        List<Object> calls = new ArrayList<>();

        assertThat(st.expressionCount(), is(2));
        assertThat(st.singularExpression(), nullValue());
        assertThat(st.resolve(2, new Object[] {"abc", "def"}), equalTo("https://foo.abc.def.com"));

        st.forEachExpression(calls::add);
        assertThat(calls, hasSize(2));
    }

    @Test
    public void ensuresTemplatesAreNotMissing() {
        var template = Template.fromString("https://foo.{Region}.{Other}.com");
        var st = StringTemplate.from(template);

        Assertions.assertThrows(RulesEvaluationError.class, () -> st.resolve(1, new Object[] {"foo"}));
    }
}
