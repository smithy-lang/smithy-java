/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.client.awsjson;

import java.util.Objects;
import software.amazon.smithy.aws.traits.protocols.AwsJson1_1Trait;
import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.runtime.client.core.ProtocolSettings;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Implements aws.protocols#awsJson1_1.
 */
public final class AwsJson11Protocol extends AwsJsonProtocol {

    private static final ShapeId TRAIT_ID = AwsJson1_1Trait.ID;

    /**
     * @param service The service ID used to make X-Amz-Target, and the namespace is used when finding the
     *                discriminator of documents that use relative shape IDs.
     */
    public AwsJson11Protocol(ShapeId service) {
        super(TRAIT_ID, service);
    }

    @Override
    protected String contentType() {
        return "application/x-amz-json-1.1";
    }

    public static final class Factory implements ClientProtocolFactory<AwsJson1_1Trait> {
        @Override
        public ShapeId id() {
            return TRAIT_ID;
        }

        @Override
        public ClientProtocol<?, ?> createProtocol(ProtocolSettings settings, AwsJson1_1Trait trait) {
            return new AwsJson11Protocol(
                Objects.requireNonNull(
                    settings.service(),
                    "service is a required protocol setting"
                )
            );
        }
    }
}
