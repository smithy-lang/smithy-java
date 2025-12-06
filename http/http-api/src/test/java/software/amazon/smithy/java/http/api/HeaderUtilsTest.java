/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class HeaderUtilsTest {

    // Name normalization tests

    @Test
    void normalizeName_lowercasesUppercase() {
        assertEquals("content-type", HeaderUtils.normalizeName("Content-Type"));
        assertEquals("x-amz-date", HeaderUtils.normalizeName("X-AMZ-DATE"));
    }

    @Test
    void normalizeName_trimsOws() {
        assertEquals("foo", HeaderUtils.normalizeName(" foo"));
        assertEquals("foo", HeaderUtils.normalizeName("foo "));
        assertEquals("foo", HeaderUtils.normalizeName("\tfoo\t"));
        assertEquals("foo", HeaderUtils.normalizeName("  foo  "));
    }

    @Test
    void normalizeName_returnsSameInstanceWhenNormalized() {
        String name = "content-type";
        assertSame(name, HeaderUtils.normalizeName(name));
    }

    @Test
    void normalizeName_rejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeName(""));
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeName("   "));
    }

    @Test
    void normalizeName_rejectsInvalidChars() {
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeName("foo:bar"));
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeName("foo\nbar"));
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeName("foo\rbar"));
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeName("foo bar"));
    }

    @Test
    void normalizeName_rejectsNonAscii() {
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeName("föo"));
    }

    // Value normalization tests

    @Test
    void normalizeValue_trimsOws() {
        assertEquals("bar", HeaderUtils.normalizeValue(" bar"));
        assertEquals("bar", HeaderUtils.normalizeValue("bar "));
        assertEquals("bar", HeaderUtils.normalizeValue("\tbar\t"));
    }

    @Test
    void normalizeValue_allowsInteriorWhitespace() {
        assertEquals("foo bar", HeaderUtils.normalizeValue("foo bar"));
        assertEquals("foo\tbar", HeaderUtils.normalizeValue("foo\tbar"));
    }

    @Test
    void normalizeValue_allowsEmpty() {
        assertEquals("", HeaderUtils.normalizeValue(""));
        assertEquals("", HeaderUtils.normalizeValue("   "));
    }

    @Test
    void normalizeValue_returnsSameInstanceWhenNormalized() {
        String value = "bar";
        assertSame(value, HeaderUtils.normalizeValue(value));
    }

    @Test
    void normalizeValue_rejectsCrLf() {
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeValue("foo\nbar"));
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeValue("foo\rbar"));
    }

    @Test
    void normalizeValue_rejectsControlChars() {
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeValue("foo\u0000bar"));
        assertThrows(IllegalArgumentException.class, () -> HeaderUtils.normalizeValue("foo\u001Fbar"));
    }

    @Test
    void normalizeValue_allowsObsText() {
        // obs-text (0x80-0xFF) is allowed
        assertEquals("foo\u0080bar", HeaderUtils.normalizeValue("foo\u0080bar"));
    }
}
