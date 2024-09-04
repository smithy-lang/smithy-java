/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.server.Service;

public final class HandlerAssembler {

    //TODO Flesh this out. Validate if all handlers actually accept the same type of Job.
    public List<Handler> assembleHandlers(List<Service> services) {
        List<Handler> handlers = new ArrayList<>();
        handlers.add(new ProtocolHandler());
        handlers.add(new OperationHandler());
        return handlers;
    }

}
