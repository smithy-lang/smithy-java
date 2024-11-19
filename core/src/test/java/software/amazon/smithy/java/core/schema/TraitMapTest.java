/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.SensitiveTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;

public class TraitMapTest {
    @Test
    public void emptyTraitMap() {
        var tm = TraitMap.create();

        assertThat(tm.get(TraitKey.REQUIRED_TRAIT), is(nullValue()));
    }

    @Test
    public void emptyTraitMapWithMemberTraits() {
        var tm = TraitMap.create();
        var tm2 = tm.withMemberTraits(TraitMap.create(new SensitiveTrait()));

        assertThat(tm.get(TraitKey.SENSITIVE_TRAIT), is(nullValue()));
        assertThat(tm2.get(TraitKey.SENSITIVE_TRAIT), instanceOf(SensitiveTrait.class));
    }

    @Test
    public void emptyGivenTraitMapWithMemberTraits() {
        var tm = TraitMap.create(new SensitiveTrait());
        var tm2 = tm.withMemberTraits(TraitMap.create());

        assertThat(tm.get(TraitKey.SENSITIVE_TRAIT), not(nullValue()));
        assertThat(tm, sameInstance(tm2));
    }

    @Test
    public void withMemberTraitsWithSmallerMin() {
        var tm = TraitMap.create(new DefaultTrait(Node.from(1)));
        var tm2 = tm.withMemberTraits(TraitMap.create(new RequiredTrait()));

        assertThat(tm2.get(TraitKey.REQUIRED_TRAIT), instanceOf(RequiredTrait.class));
        assertThat(tm2.get(TraitKey.DEFAULT_TRAIT), instanceOf(DefaultTrait.class));
    }

    @Test
    public void withMemberTraitsWithLargerMax() {
        var tm = TraitMap.create(new RequiredTrait());
        var tm2 = tm.withMemberTraits(TraitMap.create(new DefaultTrait(Node.from(1))));

        assertThat(tm2.get(TraitKey.REQUIRED_TRAIT), instanceOf(RequiredTrait.class));
        assertThat(tm2.get(TraitKey.DEFAULT_TRAIT), instanceOf(DefaultTrait.class));
    }

    @Test
    public void withMemberTraitsWithMuchSmallerIndex() {
        var tm = TraitMap.create(new XmlNameTrait("hi"));
        var tm2 = tm.withMemberTraits(TraitMap.create(new DefaultTrait(Node.from(1))));

        assertThat(tm2.get(TraitKey.XML_NAME_TRAIT), instanceOf(XmlNameTrait.class));
        assertThat(tm2.get(TraitKey.DEFAULT_TRAIT), instanceOf(DefaultTrait.class));
    }
}
