/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

/**
 * Mutable holder for JSON parse results, avoiding allocation of result arrays on every parse call.
 *
 * <p>Used as a parameter type for {@link JsonReadUtils} overloads that support runtime-generated
 * deserializers. Thread-safety is not required — each instance is used by a single thread.
 */
public class JsonParseState {
    public long parsedLong;
    public int parsedEndPos;
    public double parsedDouble;
    public String parsedString;
}
