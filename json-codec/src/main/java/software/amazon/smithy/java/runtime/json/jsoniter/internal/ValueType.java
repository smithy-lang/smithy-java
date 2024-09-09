/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal;

public enum ValueType {
    INVALID,
    STRING,
    NUMBER,
    NULL,
    BOOLEAN,
    ARRAY,
    OBJECT
}
