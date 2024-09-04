/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocols.restjson.router;

import java.util.List;

final class NullMatch implements Match {
    @Override
    public List<String> getLabelValues(String label) {
        return null;
    }

    @Override
    public boolean isPathLabel(String label) {
        return false;
    }
}
