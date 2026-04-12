/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.fuzz.DifferentialCodecFuzzTestBase;
import software.amazon.smithy.java.json.jackson.JacksonJsonSerdeProvider;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

/**
 * Differential fuzz test comparing Jackson (reference) and Smithy native (test) JSON codecs.
 *
 * <p>Each fuzz input is deserialized through both codecs using every available shape type.
 * Divergences that are not covered by an acceptability predicate cause test failure.
 */
class DifferentialJsonFuzzTest extends DifferentialCodecFuzzTestBase {

    private static final JsonCodec JACKSON_CODEC =
            JsonCodec.builder().overrideSerdeProvider(new JacksonJsonSerdeProvider()).build();
    private static final JsonCodec SMITHY_CODEC =
            JsonCodec.builder().overrideSerdeProvider(new SmithyJsonSerdeProvider()).build();

    @Override
    protected Codec referenceCodec() {
        return JACKSON_CODEC;
    }

    @Override
    protected Codec testCodec() {
        return SMITHY_CODEC;
    }

    @Override
    protected boolean isAcceptableDivergence(
            SerializableShape referenceShape,
            SerializableShape testShape,
            byte[] input
    ) {
        // Both codecs may handle malformed input (e.g., invalid UTF-8 bytes in strings)
        // differently while still producing valid output. Jackson and Smithy use different
        // replacement strategies for undecodable bytes, which causes divergence in the
        // re-serialized form. This is acceptable since the input was malformed to begin with.
        return containsNonAsciiBytes(input);
    }

    private static boolean containsNonAsciiBytes(byte[] input) {
        for (byte b : input) {
            if (b < 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isAcceptableTestFailure(SerializableShape referenceShape, Exception testError, byte[] input) {
        // Invalid bytes in JSON input cause unpredictable parsing divergence between codecs.
        if (containsNonAsciiBytes(input)) {
            return true;
        }

        String msg = getFullErrorMessage(testError);

        // Jackson coerces JSON types more liberally than Smithy. Examples:
        // - Arrays as blob values: Jackson reads [65,66] as byte array, Smithy expects base64 string
        // - Numbers as strings: Jackson coerces bare numbers to strings, Smithy expects quoted strings
        // - Numbers/strings for struct fields: Jackson is lenient with type mismatches
        if (isTypeMismatchError(msg)) {
            return true;
        }

        // Smithy validates timestamp ranges strictly. Jackson parses numbers without
        // checking if they're valid epoch seconds, so huge longs succeed in Jackson
        // but fail Smithy's range check.
        if (isTimestampRangeError(msg)) {
            return true;
        }

        // Jackson coerces floating-point numbers to integer types by truncation
        // (e.g., 0.5 -> 0 for byte/short/int/long fields). Smithy's strict integer
        // parser reads the integer part then encounters the '.' as unexpected.
        if (isNumberCoercionError(msg)) {
            return true;
        }

        // Smithy validates base64 blob values strictly while Jackson is more lenient.
        if (isBase64ValidationError(msg)) {
            return true;
        }

        // Jackson silently skips null entries in typed lists/maps while Smithy
        // rejects them (null is not a valid float/int/string/etc.)
        if (isNullInCollectionError(msg)) {
            return true;
        }

        // Smithy enforces union constraints (only one value may be set),
        // while Jackson may silently skip extra or null union members.
        if (msg.contains("Only one value may be set for unions")) {
            return true;
        }

        // Smithy native parser is stricter than Jackson on string encoding:
        // - Lone surrogate pairs in strings (\uD800 without matching \uDC00)
        // - Overlong UTF-8 byte sequences
        // - Unescaped control characters in string values
        if (isLoneSurrogateError(msg)
                || isOverlongUtf8Error(msg)
                || isControlCharError(msg)) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean isAcceptableReferenceFailure(
            SerializableShape testShape,
            Exception referenceError,
            byte[] input
    ) {
        // Invalid bytes in JSON input cause unpredictable parsing divergence between codecs.
        if (containsNonAsciiBytes(input)) {
            return true;
        }

        String msg = getFullErrorMessage(referenceError);

        // Smithy is more lenient with base64 decoding (accepts non-padded input),
        // while Jackson's MIME-NO-LINEFEEDS variant requires proper padding ('=' characters).
        if (isBase64PaddingError(msg)) {
            return true;
        }

        return false;
    }

    private static String getFullErrorMessage(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(" ");
            }
        }
        return sb.toString();
    }

    private static boolean isTypeMismatchError(String message) {
        // Smithy expects a specific JSON token type but got a different one.
        // Jackson coerces types more liberally (arrays → blobs, numbers → strings, etc.)
        return message.contains("Expected '\"', found:")
                || message.contains("Expected '{', found:")
                || message.contains("Expected a list, but found")
                || message.contains("Expected a timestamp, but found");
    }

    private static boolean isNumberCoercionError(String message) {
        // Jackson coerces floats/scientific notation to integers by truncation. Smithy
        // reads the integer part strictly, then sees unexpected chars. Manifests as:
        // 1. '.' after integer part (fractional numbers)
        // 2. 'e'/'E' after integer part (scientific notation like -35e-32434)
        // 3. Number overflow when the value exceeds the target integer range
        // 4. NumberFormatException when parseLong receives a non-integer string
        return message.contains("found '.'")
                || message.contains("found: '.'")
                || message.contains("found 'e'")
                || message.contains("found: 'e'")
                || message.contains("found 'E'")
                || message.contains("found: 'E'")
                || message.contains("Number overflow")
                || message.contains("out of byte range")
                || message.contains("out of short range")
                || message.contains("out of integer range")
                || message.contains("For input string:")
                || message.contains("Illegal embedded sign");
    }

    private static boolean isTimestampRangeError(String message) {
        return message.contains("Epoch seconds out of range")
                || message.contains("out of range");
    }

    private static boolean isBase64PaddingError(String message) {
        return message.contains("base64")
                || message.contains("Base64");
    }

    private static boolean isNullInCollectionError(String message) {
        return message.contains("Null value")
                || message.contains("null value");
    }

    private static boolean isBase64ValidationError(String message) {
        return message.contains("Invalid base64")
                || message.contains("invalid base64");
    }

    private static boolean isLoneSurrogateError(String message) {
        return message.contains("surrogate")
                || message.contains("Surrogate");
    }

    private static boolean isOverlongUtf8Error(String message) {
        return message.contains("Overlong")
                || message.contains("overlong")
                || message.contains("UTF-8")
                || message.contains("utf-8");
    }

    private static boolean isControlCharError(String message) {
        return message.contains("control character")
                || message.contains("Control character")
                || message.contains("Unescaped");
    }
}
