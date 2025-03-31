/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.server.restjson.router;

import java.util.List;

public interface Match {
    List<String> getLabelValues(String label);

    boolean isPathLabel(String label);
}
