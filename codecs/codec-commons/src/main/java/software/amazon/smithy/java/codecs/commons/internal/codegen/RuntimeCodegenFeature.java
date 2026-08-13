/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import java.util.Locale;

public final class RuntimeCodegenFeature {
    private static final String PROPERTY = "smithy-java.runtime-codegen";

    private RuntimeCodegenFeature() {}

    public static boolean enabled(String backend) {
        if (Runtime.version().feature() < 24) {
            return false;
        }
        String configured = System.getProperty(PROPERTY, "");
        for (String value : configured.split(",")) {
            if (backend.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
