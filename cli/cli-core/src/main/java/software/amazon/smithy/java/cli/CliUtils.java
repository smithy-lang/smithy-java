/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class CliUtils {
    private CliUtils() {}

    // TODO: add to case utils and remove this class
    public static String kebabCase(String string) {
        return CaseUtils.toSnakeCase(string).replace('_', '-');
    }
}
