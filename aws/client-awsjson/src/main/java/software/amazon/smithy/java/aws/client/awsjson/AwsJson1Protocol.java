/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsjson;

import java.util.Objects;
import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Implements aws.protocols#awsJson1_0.
 */
public final class AwsJson1Protocol extends AwsJsonProtocol {

    private static final ShapeId TRAIT_ID = AwsJson1_0Trait.ID;

    /**
     * @param service The service ID used to make X-Amz-Target, and the namespace is used when finding the
     *                discriminator of documents that use relative shape IDs.
     */
    public AwsJson1Protocol(ShapeId service) {
        super(TRAIT_ID, service);
    }

    @Override
    protected String contentType() {
        return "application/x-amz-json-1.0";
    }

    public static final class Factory implements ClientProtocolFactory<AwsJson1_0Trait> {
        @Override
        public ShapeId id() {
            return TRAIT_ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, AwsJson1_0Trait trait) {
            return new AwsJson1Protocol(
                    Objects.requireNonNull(
                            settings.service(),
                            "service is a required protocol setting"));
        }
    }
}
