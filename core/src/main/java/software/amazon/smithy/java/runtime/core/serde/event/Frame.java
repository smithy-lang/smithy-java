/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.event;

/**
 * A frame wraps the data for one event streaming event with metadata for signing and validation.
 */
public interface Frame<T> {
    T unwrap();
}
