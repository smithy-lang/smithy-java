/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.fuzz.DifferentialCodecFuzzTestBase;

class DifferentialXmlFuzzTest extends DifferentialCodecFuzzTestBase {

    private static final XmlCodec STAX_CODEC = XmlCodec.builder().useNative(false).build();
    private static final XmlCodec NATIVE_CODEC = XmlCodec.builder().useNative(true).build();

    @Override
    protected Codec referenceCodec() {
        return STAX_CODEC;
    }

    @Override
    protected Codec testCodec() {
        return NATIVE_CODEC;
    }

    @Override
    protected boolean isAcceptableDivergence(
            SerializableShape referenceShape,
            SerializableShape testShape,
            byte[] input
    ) {
        return false;
    }

    @Override
    protected boolean isAcceptableTestFailure(SerializableShape referenceShape, Exception testError, byte[] input) {
        // When input starts with <Response>, <ErrorResponse>, or <Error>, both codecs enter
        // an error-response parsing path. If the content isn't actually an error response,
        // the native parser may reject with stricter tag validation while StAX recovers
        // via error correction (producing empty struct output).
        if (input.length < 12) {
            return false;
        }
        String prefix = new String(input, 0, Math.min(input.length, 20), StandardCharsets.UTF_8);
        return prefix.startsWith("<Response")
                || prefix.startsWith("<ErrorResponse")
                || prefix.startsWith("<Error");
    }

    @Override
    protected boolean isAcceptableReferenceFailure(
            SerializableShape testShape,
            Exception referenceError,
            byte[] input
    ) {
        // The native parser is intentionally more lenient than StAX in specific areas:
        // 1. skipElement() uses depth-counting without validating intermediate tag names.
        // 2. Byte-level peeking for isNull()/text works where StAX's state machine throws.
        // 3. Error response paths (<ErrorResponse>/<Response>/<Error>) are handled differently.
        String msg = getFullErrorMessage(referenceError);
        if (msg.contains("XMLStreamException")
                || (msg.contains("element type") && msg.contains("must be terminated"))) {
            return true;
        }
        // Inputs that trigger error-response parsing may diverge in behavior
        if (input.length >= 6) {
            String prefix = new String(input,
                    0,
                    Math.min(input.length, 20),
                    StandardCharsets.UTF_8);
            if (prefix.startsWith("<Response") || prefix.startsWith("<ErrorResponse")
                    || prefix.startsWith("<Error")) {
                return true;
            }
        }
        return false;
    }

    private static String getFullErrorMessage(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(" ");
            }
            sb.append(t.getClass().getSimpleName()).append(" ");
        }
        return sb.toString();
    }
}
