/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import software.amazon.smithy.java.core.schema.SerializableStruct;

interface HttpBindingWriter {
    void write(SerializableStruct struct, HttpBindingSerializer sink);
}
