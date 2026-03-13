/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.jmespath;

import java.math.BigDecimal;
import java.time.Instant;

final class JmesPathDocumentUtils {

    static BigDecimal asBigDecimal(Instant instant) {
        // There may be a faster way to compute this, but it's unlikely to be used in practice,
        // mainly just need to compare timestamps which doesn't use this.
        BigDecimal secondsPart = BigDecimal.valueOf(instant.getEpochSecond());
        BigDecimal nanosPart = BigDecimal.valueOf(instant.getNano()).movePointLeft(9);
        return secondsPart.add(nanosPart);
    }

    private JmesPathDocumentUtils() {}
}
