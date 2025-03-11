/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PresenceTracker;
import software.amazon.smithy.java.example.standalone.model.Human;

public class RecursiveStructureTest {

    @Test
    void testPresenceTrackerCorrectlyWorks() {
        //Force human schema to load first.
        var humanSchema = Human.$SCHEMA;
        var fatherSchema = humanSchema.member("parents").member("father");
        var presenceTracker = PresenceTracker.of(fatherSchema);
        assertThat(presenceTracker.getMissingMembers()).hasSize(3);
        presenceTracker.setMember(fatherSchema.member("name"));
        presenceTracker.setMember(fatherSchema.member("age"));
        presenceTracker.setMember(fatherSchema.member("address"));
        assertThat(presenceTracker.getMissingMembers()).isEmpty();
        assertTrue(presenceTracker.allSet());
    }

}
