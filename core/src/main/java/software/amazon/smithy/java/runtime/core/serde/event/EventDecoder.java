/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.event;

import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

public interface EventDecoder<F extends Frame<?>> {

    SerializableStruct decode(F frame);

}
