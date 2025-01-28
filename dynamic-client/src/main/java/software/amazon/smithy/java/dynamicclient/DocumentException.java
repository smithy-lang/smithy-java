/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient;

import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A {@link ModeledException} that provides access to the contents of the exception as a document.
 */
public final class DocumentException extends ModeledException {

    private final WrappedDocument document;

    DocumentException(Schema schema, String message, WrappedDocument document) {
        super(schema, message);
        this.document = document;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        document.serialize(encoder);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        document.serializeMembers(serializer);
    }

    @Override
    public <T> T getMemberValue(Schema member) {
        return document.getMemberValue(member);
    }

    /**
     * Get the contents of the exception as a document.
     *
     * @return the exception contents.
     */
    public Document getContents() {
        return document;
    }

    static final class SchemaGuidedExceptionBuilder implements ShapeBuilder<ModeledException> {

        private final Schema target;
        private final SchemaGuidedDocumentBuilder delegateBuilder;

        SchemaGuidedExceptionBuilder(ShapeId service, Schema target) {
            this.target = target;
            this.delegateBuilder = new SchemaGuidedDocumentBuilder(service, target);
        }

        @Override
        public Schema schema() {
            return target;
        }

        @Override
        public DocumentException build() {
            return new DocumentException(target, target.id().getName() + " error", delegateBuilder.build());
        }

        @Override
        public SchemaGuidedExceptionBuilder deserialize(ShapeDeserializer decoder) {
            delegateBuilder.deserialize(decoder);
            return this;
        }

        @Override
        public SchemaGuidedExceptionBuilder deserializeMember(ShapeDeserializer decoder, Schema schema) {
            delegateBuilder.deserializeMember(decoder, schema.assertMemberTargetIs(target));
            return this;
        }

        @Override
        public SchemaGuidedExceptionBuilder errorCorrection() {
            delegateBuilder.errorCorrection();
            return this;
        }
    }
}
