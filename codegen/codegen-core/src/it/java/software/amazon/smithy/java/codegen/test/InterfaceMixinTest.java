/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.test.model.DetailedUser;
import software.amazon.smithy.java.codegen.test.model.HasFullName;
import software.amazon.smithy.java.codegen.test.model.HasName;
import software.amazon.smithy.java.codegen.test.model.HasTags;
import software.amazon.smithy.java.codegen.test.model.SimpleUser;
import software.amazon.smithy.java.codegen.test.model.TaggedResource;
import software.amazon.smithy.java.codegen.test.model.TaggedUser;
import software.amazon.smithy.java.codegen.test.model.UserNotFound;

class InterfaceMixinTest {

    @Test
    void singleInterfaceMixin() {
        HasName user = SimpleUser.builder().id(42).name("Bob").email("bob@test.com").build();
        assertEquals("Bob", user.getName());
        assertEquals(42, user.getId());
    }

    @Test
    void chainedInterfaceMixinHierarchy() {
        HasFullName user = DetailedUser.builder().id(7).name("Carol").lastName("Jones").age(30).build();
        assertEquals("Jones", user.getLastName());
        assertEquals(7, user.getId());
        // HasFullName extends HasName, so DetailedUser is also a HasName
        assertInstanceOf(HasName.class, user);
    }

    @Test
    void collectionMemberInterface() {
        HasTags resource = TaggedResource.builder().resourceId("r-1").tags(List.of("x")).build();
        assertEquals(List.of("x"), resource.getTags());
        assertTrue(resource.hasTags());
    }

    @Test
    void collectionMemberInterfaceUnset() {
        HasTags resource = TaggedResource.builder().resourceId("r-1").build();
        assertFalse(resource.hasTags());
    }

    @Test
    void multipleInterfaceMixins() {
        HasName user = TaggedUser.builder().id(5).name("Frank").tags(List.of("a", "b")).role("user").build();
        assertEquals("Frank", user.getName());
        assertEquals(5, user.getId());
        assertInstanceOf(HasTags.class, user);
    }

    @Test
    void errorShapeWithInterfaceMixin() {
        HasName error = UserNotFound.builder().id(1).name("missing").detail("not found").build();
        assertEquals("missing", error.getName());
        assertEquals(1, error.getId());
        assertInstanceOf(Exception.class, error);
    }

    @Test
    void simpleUserBuilderImplementsMixinBuilder() {
        assertInstanceOf(HasName.Builder.class, SimpleUser.builder());
    }

    @Test
    void detailedUserBuilderImplementsChainedMixinBuilders() {
        var builder = DetailedUser.builder();
        assertInstanceOf(HasFullName.Builder.class, builder);
        assertInstanceOf(HasName.Builder.class, builder);
    }

    @Test
    void taggedUserBuilderImplementsMultipleMixinBuilders() {
        var builder = TaggedUser.builder();
        assertInstanceOf(HasName.Builder.class, builder);
        assertInstanceOf(HasTags.Builder.class, builder);
    }

    @Test
    void errorBuilderImplementsMixinBuilder() {
        assertInstanceOf(HasName.Builder.class, UserNotFound.builder());
    }
}
