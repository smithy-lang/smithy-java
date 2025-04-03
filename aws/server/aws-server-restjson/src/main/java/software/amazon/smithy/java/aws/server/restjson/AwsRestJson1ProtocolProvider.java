/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.server.restjson;

import java.util.List;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.core.ServerProtocol;
import software.amazon.smithy.java.server.core.ServerProtocolProvider;
import software.amazon.smithy.model.shapes.ShapeId;

public class AwsRestJson1ProtocolProvider implements ServerProtocolProvider {
    @Override
    public ServerProtocol provideProtocolHandler(List<Service> candidateServices) {
        return new AwsRestJson1Protocol(candidateServices);
    }

    @Override
    public ShapeId getProtocolId() {
        return RestJson1Trait.ID;
    }

    @Override
    public int priority() {
        return 0;
    }
}
