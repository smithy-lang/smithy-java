/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

public final class Opcodes {

    private Opcodes() {}

    /**
     * The version that a rules engine program was compiled with. The version of a program must be less than or equal
     * to this version number. That is, older code can be run, but newer code cannot. The version is only incremented
     * when things like new opcodes are added. This is a single byte that appears as the first byte in the rules
     * engine bytecode. The version is a negative number to prevent accidentally treating another opcode as the version.
     */
    public static final byte VERSION = -1;

    /**
     * Push a value onto the stack. Must be followed by one unsigned byte representing the constant pool index.
     *
     * <p><code>SET_REGISTER [const:byte]</code>
     */
    static final byte LOAD_CONST = 0;

    /**
     * Push a value onto the stack. Must be followed by two bytes representing the (short) constant pool index.
     *
     * <p><code>SET_REGISTER [const:short]</code>
     */
    static final byte LOAD_CONST_W = 1;

    /**
     * Peeks the value at the top of the stack and pushes it onto the register stack of a register. Must be followed
     * by the one byte register index.
     *
     * <p><code>SET_REGISTER [register:byte]</code>
     */
    static final byte SET_REGISTER = 2;

    /**
     * Get the value of a register and push it onto the stack. Must be followed by the one byte register index.
     *
     * <p><code>LOAD_REGISTER [register:byte]</code>
     */
    static final byte LOAD_REGISTER = 3;

    /**
     * Pops a value off the stack and pushes true if it is falsey (null or false), or false if not. Implements the
     * "not" function as an opcode.
     *
     * <p><code>NOT</code>
     */
    static final byte NOT = 4;

    /**
     * Pops a value off the stack and pushes true if it is set (that is, not null). Implements the "isset" function
     * as an opcode.
     *
     * <p><code>ISSET</code>
     */
    static final byte ISSET = 5;

    /**
     * Checks if a register is set to a non-null value. Must be followed by an unsigned byte that represents the
     * register to check.
     *
     * <p><code>TEST_REGISTER_ISSET [register:byte]</code>
     */
    static final byte TEST_REGISTER_ISSET = 6;

    /**
     * Checks if a register is not set or set to a null value. Must be followed by an unsigned byte that represents
     * the register to check.
     *
     * <p><code>TEST_REGISTER_NOT_SET [register:byte]</code>
     */
    static final byte TEST_REGISTER_NOT_SET = 7;

    /**
     * Pops N values off the stack and pushes a list of those values onto the stack. Must be followed by an unsigned
     * byte that defines the number of elements in the list.
     *
     * <p><code>CREATE_LIST [size:byte]</code>
     */
    static final byte CREATE_LIST = 8;

    /**
     * Pops N*2 values off the stack (key then value), creates a map of those values, and pushes the map onto the
     * stack. Each popped key must be a string. Must be followed by an unsigned byte that defines the
     * number of entries in the map.
     *
     * <p><code>CREATE_MAP [size:byte]</code>
     */
    static final byte CREATE_MAP = 9;

    /**
     * Resolves a template string. Must be followed by two bytes, a short, that represents the constant pool index
     * that stores the StringTemplate.
     *
     * <p>The corresponding instruction has a StringTemplate that tells the VM how many values to pop off the stack.
     * The popped values fill in values into the template. The resolved template value as a string is then pushed onto
     * the stack.
     *
     * <p><code>RESOLVE_TEMPLATE [template-const:short]</code>
     */
    static final byte RESOLVE_TEMPLATE = 10;

    /**
     * Calls a function. Must be followed by a byte to provide the function index to call.
     *
     * <p>The function pops zero or more values off the stack based on the RulesFunction registered for the index,
     * and then pushes the Object result onto the stack.
     *
     * <p><code>FN [function-index:byte]</code>
     */
    static final byte FN = 11;

    /**
     * Pops the top level value and applies a getAttr expression on it, pushing the result onto the stack.
     * Must be followed by two bytes, a short, that represents the constant pool index that stores the
     * AttrExpression.
     *
     * <p><code>GET_ATTR [attr-index:short]</code>
     */
    static final byte GET_ATTR = 12;

    /**
     * Pops a value and pushes true if the value is boolean true, false if not.
     *
     * <p><code>IS_TRUE</code>
     */
    static final byte IS_TRUE = 13;

    /**
     * Checks if a register is boolean true and pushes the result onto the stack.
     *
     * <p>Must be followed by a byte that represents the register to check.
     *
     * <p><code>TEST_REGISTER_IS_TRUE [register:byte]</code>
     */
    static final byte TEST_REGISTER_IS_TRUE = 14;

    /**
     * Checks if a register is boolean false and pushes the result onto the stack.
     *
     * <p>Must be followed by a byte that represents the register to check.
     *
     * <p><code>TEST_REGISTER_IS_FALSE [register:byte]</code>
     */
    static final byte TEST_REGISTER_IS_FALSE = 15;

    /**
     * Pops the top two values off the stack and performs Objects.equals on them, pushing the result onto the stack.
     *
     * <p><code>EQUALS</code>
     */
    static final byte EQUALS = 16;

    /**
     * Pops the top value off the stack, expecting a string, and extracts a substring of it, pushing the result onto
     * the stack.
     *
     * <p>Must be followed by three bytes: the start position in the string, the end position in the string, and
     * a byte set to 1 if the substring is "reversed" (from the end) or not.
     *
     * <p><code>SUBSTRING [start:byte] [end:byte] [reversed:byte]</code>
     */
    static final byte SUBSTRING = 17;

    /**
     * Sets an error on the VM and exits. Pops a single value that provides the error string to set.
     *
     * <p><code>RETURN_ERROR</code>
     */
    static final byte RETURN_ERROR = 18;

    /**
     * Sets the endpoint result of the VM and exits. Pops the top of the stack, expecting a string value. The opcode
     * must be followed by a byte where the first bit of the byte is on if the endpoint has headers, and the second
     * bit is on if the endpoint has properties.
     *
     * <p><code>RETURN_ENDPOINT [packed-bits:byte]</code>
     */
    static final byte RETURN_ENDPOINT = 19;

    /**
     * Pops the value at the top of the stack and returns it from the VM. This can be used for testing purposes or
     * for returning things other than endpoint values.
     *
     * <p><code>RETURN_VALUE</code>
     */
    static final byte RETURN_VALUE = 20;
}
