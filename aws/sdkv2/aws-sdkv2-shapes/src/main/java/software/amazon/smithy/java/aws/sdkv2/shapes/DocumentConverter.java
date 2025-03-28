/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.sdkv2.shapes;

import java.util.Objects;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Provides protocol-specific conversions between Smithy documents and AWS SDK for Java V2 documents.
 */
public interface DocumentConverter {
    /**
     * Get a converter for the given protocol.
     *
     * @param id Protocol to use for determining how the Java SDK document is serialized.
     * @return the converter.
     * @throws IllegalArgumentException if no converter can be found.
     */
    static DocumentConverter getConverter(ShapeId id) {
        Objects.requireNonNull(id, "id cannot be null");
        var result = SdkJsonDocumentParser.CONVERTERS.get(id);
        if (result == null) {
            throw new IllegalArgumentException("Unknown document converter protocol: " + id);
        }
        return result;
    }

    /**
     * The protocol used by the converter.
     *
     * @return the converter's protocol.
     */
    ShapeId protocol();

    /**
     * Converts an SDK document to a Smithy document.
     *
     * @param sdkDocument SDK document to convert.
     * @return the created Smithy document, or null if the SDK document is a null value.
     */
    Document sdkToSmithy(software.amazon.awssdk.core.document.Document sdkDocument);

    /**
     * Converts a Smithy document to an SDK document.
     *
     * @param document Smithy document to convert, or null if the Smithy document is null.
     * @return the created SDK document.
     */
    software.amazon.awssdk.core.document.Document smithyToSdk(Document document);
}
