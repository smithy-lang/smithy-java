/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RetryableTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

public class TraitsTest {

    private static List<Arguments> memberSchemaSource() {
        return List.of(
                Arguments.of("stringDefault", DefaultTrait.class, new DefaultTrait(Node.from("string"))),
                Arguments.of("stringWithLength", LengthTrait.class, LengthTrait.builder().min(10L).build()),
                Arguments.of("numberWithRange",
                        RangeTrait.class,
                        RangeTrait.builder().max(new BigDecimal("100")).build()),
                Arguments.of(
                        "xmlNamespaced",
                        XmlNamespaceTrait.class,
                        XmlNamespaceTrait.builder().uri("http://foo.com").build()));
    }

    @ParameterizedTest
    @MethodSource("memberSchemaSource")
    void testStructureMemberSchemaTraitsSet(String memberName, Class<? extends Trait> traitClass, Trait expected) {
        var memberSchema = TraitsInput.$SCHEMA.member(memberName);
        var traitValue = memberSchema.expectTrait(TraitKey.get(traitClass));
        assertEquals(traitValue, expected);
    }

    @Test
    void testErrorTraitsSet() {
        var retryableTrait = RetryableError.$SCHEMA.expectTrait(TraitKey.get(RetryableTrait.class));
        assertEquals(retryableTrait, RetryableTrait.builder().build());
    }
}
