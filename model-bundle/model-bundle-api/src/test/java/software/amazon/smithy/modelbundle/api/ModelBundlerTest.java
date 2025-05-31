/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelBundlerTest {

    @Test
    void testExplicitlyBlockedOperation() {
        Set<String> allowedOps = Set.of("allowed1", "allowed2");
        Set<String> blockedOps = Set.of("blocked1", "blocked2");
        Set<String> allowedPrefixes = Set.of("allow_");
        Set<String> blockedPrefixes = Set.of("block_");

        assertFalse(ModelBundler.isAllowed("blocked1", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }

    @Test
    void testExplicitlyAllowedOperation() {
        Set<String> allowedOps = Set.of("allowed1", "allowed2");
        Set<String> blockedOps = Set.of("blocked1", "blocked2");
        Set<String> allowedPrefixes = Set.of("allow_");
        Set<String> blockedPrefixes = Set.of("block_");

        assertTrue(ModelBundler.isAllowed("allowed1", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }

    @Test
    void testBlockedPrefixOperation() {
        Set<String> allowedOps = Set.of();
        Set<String> blockedOps = Set.of();
        Set<String> allowedPrefixes = Set.of("allow_");
        Set<String> blockedPrefixes = Set.of("block_");

        assertFalse(ModelBundler.isAllowed("block_test", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }

    @Test
    void testAllowedPrefixOperation() {
        Set<String> allowedOps = Set.of();
        Set<String> blockedOps = Set.of();
        Set<String> allowedPrefixes = Set.of("allow_");
        Set<String> blockedPrefixes = Set.of("block_");

        assertTrue(ModelBundler.isAllowed("allow_test", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }

    @Test
    void testImplicitlyAllowedWhenNoAllowLists() {
        Set<String> allowedOps = Set.of();
        Set<String> blockedOps = Set.of();
        Set<String> allowedPrefixes = Set.of();
        Set<String> blockedPrefixes = Set.of();

        assertTrue(ModelBundler.isAllowed("any_operation", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }

    @Test
    void testImplicitlyBlockedWhenNotInAllowLists() {
        Set<String> allowedOps = Set.of("allowed1");
        Set<String> blockedOps = Set.of();
        Set<String> allowedPrefixes = Set.of("allow_");
        Set<String> blockedPrefixes = Set.of();

        assertFalse(
                ModelBundler.isAllowed("unknown_operation", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }

    @Test
    void testBlockedTakesPrecedenceOverAllowed() {
        Set<String> allowedOps = Set.of("operation1");
        Set<String> blockedOps = Set.of("operation1");
        Set<String> allowedPrefixes = Set.of();
        Set<String> blockedPrefixes = Set.of();

        assertFalse(ModelBundler.isAllowed("operation1", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }

    @Test
    void testBlockedPrefixTakesPrecedenceOverAllowedPrefix() {
        Set<String> allowedOps = Set.of();
        Set<String> blockedOps = Set.of();
        Set<String> allowedPrefixes = Set.of("test_");
        Set<String> blockedPrefixes = Set.of("test_");

        assertFalse(ModelBundler.isAllowed("test_operation", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }

    @Test
    void testAllowedTakesPrecedenceOverBlockedPrefix() {
        Set<String> allowedOps = Set.of("operation1");
        Set<String> blockedOps = Set.of();
        Set<String> allowedPrefixes = Set.of();
        Set<String> blockedPrefixes = Set.of("operation");

        assertTrue(ModelBundler.isAllowed("operation1", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }

    @Test
    void testBlockedTakesPrecedenceOverAllowedPrefix() {
        Set<String> allowedOps = Set.of();
        Set<String> blockedOps = Set.of("operation1");
        Set<String> allowedPrefixes = Set.of("operation");
        Set<String> blockedPrefixes = Set.of();

        assertFalse(ModelBundler.isAllowed("operation1", allowedOps, blockedOps, allowedPrefixes, blockedPrefixes));
    }
}
