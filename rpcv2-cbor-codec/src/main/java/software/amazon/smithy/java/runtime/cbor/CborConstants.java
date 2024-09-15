/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

final class CborConstants {
    static final int MAJOR_TYPE_SHIFT = 5,
        MAJOR_TYPE_MASK = 0b111_00000,
        MINOR_TYPE_MASK = 0b0001_1111;

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

    static final int ZERO_BYTES = 23,
        ONE_BYTE = 24,
        TWO_BYTES = 25,
        FOUR_BYTES = 26,
        EIGHT_BYTES = 27,
        INDEFINITE = 31;

    static final int TYPE_POSINT_1 = TYPE_POSINT | ONE_BYTE,
        TYPE_POSINT_2 = TYPE_POSINT | TWO_BYTES,
        TYPE_POSINT_4 = TYPE_POSINT | FOUR_BYTES,
        TYPE_POSINT_8 = TYPE_POSINT | EIGHT_BYTES,
        TYPE_NEGINT_1 = TYPE_NEGINT | ONE_BYTE,
        TYPE_NEGINT_2 = TYPE_NEGINT | TWO_BYTES,
        TYPE_NEGINT_4 = TYPE_NEGINT | FOUR_BYTES,
        TYPE_NEGINT_8 = TYPE_NEGINT | EIGHT_BYTES;

    static final int SIMPLE_FALSE = 20,
        SIMPLE_TRUE = 21,
        SIMPLE_NULL = 22,
        SIMPLE_UNDEFINED = 23,
        SIMPLE_VALUE_1 = 24, // value follows in next 1 byte, currently reserved and unused
        SIMPLE_HALF_FLOAT = 25,
        SIMPLE_FLOAT = 26,
        SIMPLE_DOUBLE = 27,
        SIMPLE_BREAK = INDEFINITE;

    static final int TYPE_SIMPLE_FALSE = TYPE_SIMPLE | SIMPLE_FALSE,
        TYPE_SIMPLE_TRUE = TYPE_SIMPLE | SIMPLE_TRUE,
        TYPE_SIMPLE_NULL = TYPE_SIMPLE | SIMPLE_NULL,
        TYPE_SIMPLE_UNDEFINED = TYPE_SIMPLE | SIMPLE_UNDEFINED,
        TYPE_SIMPLE_HALF_FLOAT = TYPE_SIMPLE | SIMPLE_HALF_FLOAT,
        TYPE_SIMPLE_FLOAT = TYPE_SIMPLE | SIMPLE_FLOAT,
        TYPE_SIMPLE_DOUBLE = TYPE_SIMPLE | SIMPLE_DOUBLE,
        TYPE_SIMPLE_BREAK_STREAM = TYPE_SIMPLE | INDEFINITE;


    static final byte TAG_TIME_RFC3339 = 0, // expect text string
        TAG_TIME_EPOCH = 1, // expect integer or float
        TAG_POS_BIGNUM = 2, // expect byte string
        TAG_NEG_BIGNUM = 3, // expect byte string
        TAG_DECIMAL = 4, // expect two-element integer array
        TAG_BIG_FLOAT = 5; // expect two-element integer array
}
