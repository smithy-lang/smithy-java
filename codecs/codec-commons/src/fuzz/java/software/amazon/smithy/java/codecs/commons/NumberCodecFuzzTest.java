/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

class NumberCodecFuzzTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @FuzzTest
    void fuzzParseInt(byte[] input) {
        String str = new String(input, StandardCharsets.US_ASCII);
        Integer jdkResult = null;
        Exception jdkError = null;
        try {
            jdkResult = Integer.parseInt(str);
        } catch (Exception e) {
            jdkError = e;
        }

        Integer testResult = null;
        Exception testError = null;
        try {
            testResult = assertTimeoutPreemptively(TIMEOUT, () -> NumberCodec.parseInt(input, 0, input.length));
        } catch (Exception e) {
            testError = e;
        }

        if (jdkError != null && testResult != null) {
            throw new AssertionError(
                    "JDK failed but NumberCodec succeeded for: \"" + str + "\". JDK error: " + jdkError.getMessage()
                            + ", NumberCodec result: " + testResult,
                    jdkError);
        }
        if (jdkError == null && testError != null) {
            throw new AssertionError(
                    "JDK succeeded but NumberCodec failed for: \"" + str + "\". JDK result: " + jdkResult
                            + ", NumberCodec error: " + testError.getMessage(),
                    testError);
        }
        if (jdkError != null) {
            return;
        }
        assertEquals(jdkResult, testResult, "parseInt mismatch for: \"" + str + "\"");
    }

    @FuzzTest
    void fuzzParseLong(byte[] input) {
        String str = new String(input, StandardCharsets.US_ASCII);
        Long jdkResult = null;
        Exception jdkError = null;
        try {
            jdkResult = Long.parseLong(str);
        } catch (Exception e) {
            jdkError = e;
        }

        Long testResult = null;
        Exception testError = null;
        try {
            testResult = assertTimeoutPreemptively(TIMEOUT, () -> NumberCodec.parseLong(input, 0, input.length));
        } catch (Exception e) {
            testError = e;
        }

        if (jdkError != null && testResult != null) {
            throw new AssertionError(
                    "JDK failed but NumberCodec succeeded for: \"" + str + "\". JDK error: " + jdkError.getMessage()
                            + ", NumberCodec result: " + testResult,
                    jdkError);
        }
        if (jdkError == null && testError != null) {
            throw new AssertionError(
                    "JDK succeeded but NumberCodec failed for: \"" + str + "\". JDK result: " + jdkResult
                            + ", NumberCodec error: " + testError.getMessage(),
                    testError);
        }
        if (jdkError != null) {
            return;
        }
        assertEquals(jdkResult, testResult, "parseLong mismatch for: \"" + str + "\"");
    }

    @FuzzTest
    void fuzzWriteInt(int value) {
        String expected = Integer.toString(value);
        byte[] buf = new byte[12];
        int endPos = assertTimeoutPreemptively(TIMEOUT, () -> NumberCodec.writeInt(buf, 0, value));
        String actual = new String(buf, 0, endPos, StandardCharsets.US_ASCII);
        assertEquals(expected, actual, "writeInt mismatch for: " + value);
    }

    @FuzzTest
    void fuzzWriteLong(long value) {
        String expected = Long.toString(value);
        byte[] buf = new byte[21];
        int endPos = assertTimeoutPreemptively(TIMEOUT, () -> NumberCodec.writeLong(buf, 0, value));
        String actual = new String(buf, 0, endPos, StandardCharsets.US_ASCII);
        assertEquals(expected, actual, "writeLong mismatch for: " + value);
    }

    @FuzzTest
    void fuzzWriteDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return;
        }
        byte[] buf = new byte[32];
        int endPos = assertTimeoutPreemptively(TIMEOUT, () -> NumberCodec.writeDouble(buf, 0, value));
        String actual = new String(buf, 0, endPos, StandardCharsets.US_ASCII);
        double roundTrip = Double.parseDouble(actual);
        assertEquals(value, roundTrip, "writeDouble round-trip mismatch for: " + value);
    }

    @FuzzTest
    void fuzzWriteFloat(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return;
        }
        byte[] buf = new byte[20];
        int endPos = assertTimeoutPreemptively(TIMEOUT, () -> NumberCodec.writeFloat(buf, 0, value));
        String actual = new String(buf, 0, endPos, StandardCharsets.US_ASCII);
        float roundTrip = Float.parseFloat(actual);
        assertEquals(value, roundTrip, "writeFloat round-trip mismatch for: " + value);
    }
}
