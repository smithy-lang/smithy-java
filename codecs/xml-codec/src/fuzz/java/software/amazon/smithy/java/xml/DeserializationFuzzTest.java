/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import javax.xml.stream.XMLStreamException;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.fuzz.CodecDeserializationFuzzTestBase;

/**
 * Fuzz tests for XmlCodec with various configurations.
 */
class DeserializationFuzzTest {

    static class DefaultTest extends CodecDeserializationFuzzTestBase {

        @Override
        protected Codec codecToFuzz() {
            return XmlCodec.builder().build();
        }

        @Override
        protected boolean isErrorAcceptable(Exception exception) throws Exception {
            if (exception instanceof XMLStreamException
                    || exception.getCause() instanceof XMLStreamException) {
                return true;
            }
            return super.isErrorAcceptable(exception);
        }
    }

}
