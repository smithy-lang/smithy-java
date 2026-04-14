/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.fuzz.CodecDeserializationFuzzTestBase;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;

class SmithyJsonFuzzTest extends CodecDeserializationFuzzTestBase {
    private static final JsonCodec CODEC =
            JsonCodec.builder().overrideSerdeProvider(new SmithyJsonSerdeProvider()).build();

    @Override
    protected Codec codecToFuzz() {
        return CODEC;
    }

    @Override
    @SuppressFBWarnings
    protected boolean isErrorAcceptable(Exception exception) {
        try {
            throw exception;
        } catch (NullPointerException npe) {
            return switch (npe.getMessage()) {
                case "no union value set" -> true;
                case null, default -> false;
            };
        } catch (IllegalArgumentException iae) {
            return switch (iae.getMessage()) {
                case "Only one value may be set for unions" -> true;
                case null, default -> false;
            };
        } catch (SerializationException ignored) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
