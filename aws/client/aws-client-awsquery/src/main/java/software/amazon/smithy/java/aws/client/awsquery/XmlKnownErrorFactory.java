/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.client.http.HttpErrorDeserializer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Shared error factory for XML-based query protocols.
 */
final class XmlKnownErrorFactory implements HttpErrorDeserializer.KnownErrorFactory {
    @Override
    public ModeledException createError(
            Context context,
            Codec codec,
            HttpResponse response,
            ShapeBuilder<ModeledException> builder
    ) {
        ByteBuffer bytes = DataStream.ofPublisher(
                response.body(),
                response.contentType(),
                response.contentLength(-1)).asByteBuffer();
        return codec.deserializeShape(bytes, builder);
    }
}
