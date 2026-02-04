/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.nio.ByteBuffer;
import java.util.List;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.xml.XmlCodec;

final class AwsQueryXmlResponseDeserializer {

    private final ByteBuffer source;
    private final String operationName;

    AwsQueryXmlResponseDeserializer(ByteBuffer source, String operationName) {
        this.source = source;
        this.operationName = operationName;
    }

    <T extends SerializableStruct> T deserialize(ShapeBuilder<T> builder) {
        try (var codec = XmlCodec.builder()
                .wrapperElements(List.of(operationName + "Response", operationName + "Result"))
                .build()) {
            return codec.deserializeShape(source, builder);
        }
    }
}
