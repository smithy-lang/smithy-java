/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import software.amazon.smithy.protocoltests.traits.AppliesTo;

public enum TestType {
    CLIENT(AppliesTo.CLIENT),
    SERVER(AppliesTo.SERVER);

    final AppliesTo appliesTo;

    TestType(AppliesTo appliesTo) {
        this.appliesTo = appliesTo;
    }
}
