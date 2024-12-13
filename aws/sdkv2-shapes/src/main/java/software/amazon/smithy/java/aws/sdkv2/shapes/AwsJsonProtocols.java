/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.sdkv2.shapes;

import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait;
import software.amazon.smithy.aws.traits.protocols.AwsJson1_1Trait;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Contains implementations of the built-in Smithy to/from Java SDK document protocol implementations.
 */
public enum AwsJsonProtocols implements DocumentConverter {
    /**
     * Converts AWS SDK documents using "aws.protocols#awsJson1_0".
     *
     * <p>The {@code jsonName} and {@code timestampFormat} traits are not used with this protocol.
     * Timestamps default to the {@code epoch-seconds} format.
     */
    AWS_JSON_1(new AwsJson1DocumentConverter()),

    /**
     * Converts AWS SDK documents using "aws.protocols#awsJson1_1".
     *
     * <p>The {@code jsonName} and {@code timestampFormat} traits are not used with this protocol.
     * Timestamps default to the {@code epoch-seconds} format.
     */
    AWS_JSON_1_1(new AwsJson11DocumentConverter()),

    /**
     * Converts AWS SDK documents using "aws.protocols#restJson1".
     *
     * <p>The {@code jsonName} and {@code timestampFormat} traits are used with this protocol.
     * Timestamps default to the {@code epoch-seconds} format.
     */
    REST_JSON_1(new RestJson1DocumentConverter());

    private final DocumentConverter delegate;

    AwsJsonProtocols(DocumentConverter delegate) {
        this.delegate = delegate;
    }

    @Override
    public ShapeId protocol() {
        return delegate.protocol();
    }

    @Override
    public Document sdkToSmithy(software.amazon.awssdk.core.document.Document sdkDocument) {
        return delegate.sdkToSmithy(sdkDocument);
    }

    @Override
    public software.amazon.awssdk.core.document.Document smithyToSdk(Document document) {
        return delegate.smithyToSdk(document);
    }

    @SmithyInternalApi
    public static class AwsJson1DocumentConverter implements DocumentConverter {
        @Override
        public ShapeId protocol() {
            return AwsJson1_0Trait.ID;
        }

        @Override
        public Document sdkToSmithy(software.amazon.awssdk.core.document.Document sdkDocument) {
            return sdkDocument.accept(new SdkJsonDocumentVisitor(SdkJsonDocumentParser.JSON));
        }

        @Override
        public software.amazon.awssdk.core.document.Document smithyToSdk(Document document) {
            return SdkJsonDocumentParser.writeJsonDocument(document, SdkJsonDocumentParser.JSON);
        }
    }

    @SmithyInternalApi
    public static class AwsJson11DocumentConverter implements DocumentConverter {
        @Override
        public ShapeId protocol() {
            return AwsJson1_1Trait.ID;
        }

        @Override
        public Document sdkToSmithy(software.amazon.awssdk.core.document.Document sdkDocument) {
            return sdkDocument.accept(new SdkJsonDocumentVisitor(SdkJsonDocumentParser.JSON));
        }

        @Override
        public software.amazon.awssdk.core.document.Document smithyToSdk(Document document) {
            return SdkJsonDocumentParser.writeJsonDocument(document, SdkJsonDocumentParser.JSON);
        }
    }

    @SmithyInternalApi
    public static class RestJson1DocumentConverter implements DocumentConverter {
        @Override
        public ShapeId protocol() {
            return RestJson1Trait.ID;
        }

        @Override
        public Document sdkToSmithy(software.amazon.awssdk.core.document.Document sdkDocument) {
            return sdkDocument.accept(new SdkJsonDocumentVisitor(SdkJsonDocumentParser.REST_JSON));
        }

        @Override
        public software.amazon.awssdk.core.document.Document smithyToSdk(Document document) {
            return SdkJsonDocumentParser.writeJsonDocument(document, SdkJsonDocumentParser.REST_JSON);
        }
    }
}
