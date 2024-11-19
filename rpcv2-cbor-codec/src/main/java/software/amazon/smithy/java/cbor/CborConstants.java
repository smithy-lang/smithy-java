/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

final class CborConstants {
    static final int MAJOR_TYPE_SHIFT = 5;

    static final byte MAJOR_TYPE_POSINT = 0,
        MAJOR_TYPE_NEGINT = 1,
        MAJOR_TYPE_BYTESTRING = 2,
        MAJOR_TYPE_TEXTSTRING = 3,
        MAJOR_TYPE_ARRAY = 4,
        MAJOR_TYPE_MAP = 5,
        MAJOR_TYPE_TAG = 6,
        MAJOR_TYPE_SIMPLE = 7;

    static final int TYPE_POSINT = MAJOR_TYPE_POSINT << MAJOR_TYPE_SHIFT,
        TYPE_NEGINT = MAJOR_TYPE_NEGINT << MAJOR_TYPE_SHIFT,
        TYPE_BYTESTRING = MAJOR_TYPE_BYTESTRING << MAJOR_TYPE_SHIFT,
        TYPE_TEXTSTRING = MAJOR_TYPE_TEXTSTRING << MAJOR_TYPE_SHIFT,
        TYPE_ARRAY = MAJOR_TYPE_ARRAY << MAJOR_TYPE_SHIFT,
        TYPE_MAP = MAJOR_TYPE_MAP << MAJOR_TYPE_SHIFT,
        TYPE_TAG = MAJOR_TYPE_TAG << MAJOR_TYPE_SHIFT,
        TYPE_SIMPLE = MAJOR_TYPE_SIMPLE << MAJOR_TYPE_SHIFT;

    static final int ONE_BYTE = 24,
        TWO_BYTES = 25,
        FOUR_BYTES = 26,
        EIGHT_BYTES = 27,
        INDEFINITE = 31;

    static final int SIMPLE_FALSE = 20,
        SIMPLE_TRUE = 21,
        SIMPLE_NULL = 22,
        SIMPLE_FLOAT = 26,
        SIMPLE_DOUBLE = 27;

    static final int TYPE_SIMPLE_FALSE = TYPE_SIMPLE | SIMPLE_FALSE,
        TYPE_SIMPLE_TRUE = TYPE_SIMPLE | SIMPLE_TRUE,
        TYPE_SIMPLE_NULL = TYPE_SIMPLE | SIMPLE_NULL,
        TYPE_SIMPLE_FLOAT = TYPE_SIMPLE | SIMPLE_FLOAT,
        TYPE_SIMPLE_DOUBLE = TYPE_SIMPLE | SIMPLE_DOUBLE,
        TYPE_SIMPLE_BREAK_STREAM = TYPE_SIMPLE | INDEFINITE;

    static final byte TAG_TIME_EPOCH = 1; // expect integer or float
}
