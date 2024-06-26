/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.event;

import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

public interface EventEncoder<F extends Frame<?>> {

    F encode(SerializableStruct item);

    F encodeFailure(Throwable exception);

}
