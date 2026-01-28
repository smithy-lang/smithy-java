/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.List;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;

public interface ServerProtocolProvider {

    ServerProtocol provideProtocolHandler(List<Service> candidateServices);

    ShapeId getProtocolId();

    int precision();
}
