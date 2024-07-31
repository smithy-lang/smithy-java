/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.model.shapes.ShapeId;

public abstract class ServerProtocol {

    public abstract ShapeId getProtocolId();
}
