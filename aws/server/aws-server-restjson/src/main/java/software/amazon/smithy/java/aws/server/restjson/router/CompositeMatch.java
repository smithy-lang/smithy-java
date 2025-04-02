/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.server.restjson.router;

import java.util.List;
import java.util.Objects;

class CompositeMatch implements Match {
    private final Match[] match;

    public CompositeMatch(Match... match) {
        Objects.requireNonNull(match);

        for (Match m : match) {
            if (m == null) {
                throw new IllegalArgumentException();
            }
        }

        this.match = match;
    }

    @Override
    public List<String> getLabelValues(String label) {
        for (Match m : match) {
            List<String> result = m.getLabelValues(label);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    @Override
    public boolean isPathLabel(String label) {
        for (Match m : match) {
            Iterable<String> result = m.getLabelValues(label);
            if (result != null) {
                return m.isPathLabel(label);
            }
        }
        return false;
    }
}
