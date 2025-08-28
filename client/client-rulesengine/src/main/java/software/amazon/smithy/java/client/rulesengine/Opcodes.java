/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

public final class Opcodes {

    private Opcodes() {}

    /**
     * Push a constant value onto the stack from the constant pool.
     *
     * <p>Stack: [...] => [..., value]
     *
     * <p><code>LOAD_CONST [const-index:byte]</code>
     */
    public static final byte LOAD_CONST = 0;

    /**
     * Push a constant value onto the stack from the constant pool using a wide index.
     *
     * <p>Stack: [...] => [..., value]
     *
     * <p><code>LOAD_CONST_W [const-index:short]</code>
     */
    public static final byte LOAD_CONST_W = 1;

    /**
     * Store the value at the top of the stack into a register without popping it.
     *
     * <p>Stack: [..., value] => [..., value]
     *
     * <p><code>SET_REGISTER [register:byte]</code>
     */
    public static final byte SET_REGISTER = 2;

    /**
     * Load a value from a register and push it onto the stack.
     *
     * <p>Stack: [...] => [..., value]
     *
     * <p><code>LOAD_REGISTER [register:byte]</code>
     */
    public static final byte LOAD_REGISTER = 3;

    /**
     * Replace the top stack value with its logical negation.
     *
     * <p>Stack: [..., value] => [..., !value]
     *
     * <p><code>NOT</code>
     */
    public static final byte NOT = 4;

    /**
     * Replace the top stack value with true if it's non-null, false otherwise.
     *
     * <p>Stack: [..., value] => [..., boolean]
     *
     * <p><code>ISSET</code>
     */
    public static final byte ISSET = 5;

    /**
     * Test if a register contains a non-null value and push the result.
     *
     * <p>Stack: [...] => [..., boolean]
     *
     * <p><code>TEST_REGISTER_ISSET [register:byte]</code>
     */
    public static final byte TEST_REGISTER_ISSET = 6;

    /**
     * Test if a register is null or unset and push the result.
     *
     * <p>Stack: [...] => [..., boolean]
     *
     * <p><code>TEST_REGISTER_NOT_SET [register:byte]</code>
     */
    public static final byte TEST_REGISTER_NOT_SET = 7;

    /**
     * Push an empty list onto the stack.
     *
     * <p>Stack: [...] => [..., []]
     *
     * <p><code>LIST0</code>
     */
    public static final byte LIST0 = 8;

    /**
     * Pop one value from the stack and push a single-element list.
     *
     * <p>Stack: [..., value] => [..., [value]]
     *
     * <p><code>LIST1</code>
     */
    public static final byte LIST1 = 9;

    /**
     * Pop two values from the stack and push a two-element list.
     *
     * <p>Stack: [..., value1, value2] => [..., [value1, value2]]
     *
     * <p><code>LIST2</code>
     */
    public static final byte LIST2 = 10;

    /**
     * Pop N values from the stack and push a list containing them.
     *
     * <p>Stack: [..., value1, ..., valueN] => [..., list]
     *
     * <p><code>LISTN [size:byte]</code>
     */
    public static final byte LISTN = 11;

    /**
     * Push an empty map onto the stack.
     *
     * <p>Stack: [...] => [..., {}]
     *
     * <p><code>MAP0</code>
     */
    public static final byte MAP0 = 12;

    /**
     * Pop a key-value pair from the stack and push a single-entry map.
     *
     * <p>Stack: [..., value, key] => [..., {key: value}]
     *
     * <p><code>MAP1</code>
     */
    public static final byte MAP1 = 13;

    /**
     * Pop two key-value pairs from the stack and push a two-entry map.
     *
     * <p>Stack: [..., value1, key1, value2, key2] => [..., {key1: value1, key2: value2}]
     *
     * <p><code>MAP2</code>
     */
    public static final byte MAP2 = 14;

    /**
     * Pop three key-value pairs from the stack and push a three-entry map.
     *
     * <p>Stack: [..., value1, key1, value2, key2, value3, key3] => [..., map]
     *
     * <p><code>MAP3</code>
     */
    public static final byte MAP3 = 15;

    /**
     * Pop four key-value pairs from the stack and push a four-entry map.
     *
     * <p>Stack: [..., value1, key1, value2, key2, value3, key3, value4, key4] => [..., map]
     *
     * <p><code>MAP4</code>
     */
    public static final byte MAP4 = 16;

    /**
     * Pop N key-value pairs from the stack and push a map containing them.
     *
     * <p>Stack: [..., value1, key1, ..., valueN, keyN] => [..., map]
     *
     * <p><code>MAPN [size:byte]</code>
     */
    public static final byte MAPN = 17;

    /**
     * Pop N values from the stack and resolve a string template with them.
     * The template is fetched from the constant pool and the N argument count
     * is provided as an operand to avoid storing it in the template.
     *
     * <p>Stack: [..., arg1, arg2, ..., argN] => [..., string]
     *
     * <p><code>RESOLVE_TEMPLATE [arg-count:byte] [template-index:short]</code>
     */
    public static final byte RESOLVE_TEMPLATE = 18;

    /**
     * Call a function with no arguments and push the result.
     *
     * <p>Stack: [...] => [..., result]
     *
     * <p><code>FN0 [function-index:byte]</code>
     */
    public static final byte FN0 = 19;

    /**
     * Call a function with one argument and push the result.
     *
     * <p>Stack: [..., arg] => [..., result]
     *
     * <p><code>FN1 [function-index:byte]</code>
     */
    public static final byte FN1 = 20;

    /**
     * Call a function with two arguments and push the result.
     *
     * <p>Stack: [..., arg1, arg2] => [..., result]
     *
     * <p><code>FN2 [function-index:byte]</code>
     */
    public static final byte FN2 = 21;

    /**
     * Call a function with three arguments and push the result.
     *
     * <p>Stack: [..., arg1, arg2, arg3] => [..., result]
     *
     * <p><code>FN3 [function-index:byte]</code>
     */
    public static final byte FN3 = 22;

    /**
     * Call a function with arguments from the stack and push the result.
     *
     * <p>Stack: [..., arg1, arg2, ..., argN] => [..., result]
     *
     * <p><code>FN [function-index:byte]</code>
     */
    public static final byte FN = 23;

    /**
     * Get a property from the value at the top of the stack, replacing it with the property value.
     *
     * <p>Stack: [..., object] => [..., object.property]
     *
     * <p><code>GET_PROPERTY [property-name-index:short]</code>
     */
    public static final byte GET_PROPERTY = 24;

    /**
     * Get an indexed element from the value at the top of the stack, replacing it with the element.
     *
     * <p>Stack: [..., array] => [..., array[index]]
     *
     * <p><code>GET_INDEX [index:byte]</code>
     */
    public static final byte GET_INDEX = 25;

    /**
     * Load a property from a register and push it onto the stack.
     *
     * <p>Stack: [...] => [..., register.property]
     *
     * <p><code>GET_PROPERTY_REG [register:byte] [property-name-index:short]</code>
     */
    public static final byte GET_PROPERTY_REG = 26;

    /**
     * Load an indexed element from a register and push it onto the stack.
     *
     * <p>Stack: [...] => [..., register[index]]
     *
     * <p><code>GET_INDEX_REG [register:byte] [index:byte]</code>
     */
    public static final byte GET_INDEX_REG = 27;

    /**
     * Replace the top stack value with true if it equals Boolean.TRUE, false otherwise.
     *
     * <p>Stack: [..., value] => [..., boolean]
     *
     * <p><code>IS_TRUE</code>
     */
    public static final byte IS_TRUE = 28;

    /**
     * Test if a register contains Boolean.TRUE and push the result.
     *
     * <p>Stack: [...] => [..., boolean]
     *
     * <p><code>TEST_REGISTER_IS_TRUE [register:byte]</code>
     */
    public static final byte TEST_REGISTER_IS_TRUE = 29;

    /**
     * Test if a register contains Boolean.FALSE and push the result.
     *
     * <p>Stack: [...] => [..., boolean]
     *
     * <p><code>TEST_REGISTER_IS_FALSE [register:byte]</code>
     */
    public static final byte TEST_REGISTER_IS_FALSE = 30;

    /**
     * Pop two values from the stack and push whether they are equal.
     *
     * <p>Stack: [..., value1, value2] => [..., boolean]
     *
     * <p><code>EQUALS</code>
     */
    public static final byte EQUALS = 31;

    /**
     * Pop two strings from the stack and push whether they are equal.
     * More efficient than EQUALS for string comparisons.
     *
     * <p>Stack: [..., string1, string2] => [..., boolean]
     *
     * <p><code>STRING_EQUALS</code>
     */
    public static final byte STRING_EQUALS = 32;

    /**
     * Pop two booleans from the stack and push whether they are equal.
     * More efficient than EQUALS for boolean comparisons.
     *
     * <p>Stack: [..., boolean1, boolean2] => [..., boolean]
     *
     * <p><code>BOOLEAN_EQUALS</code>
     */
    public static final byte BOOLEAN_EQUALS = 33;

    /**
     * Pop a string from the stack and push a substring of it.
     *
     * <p>Stack: [..., string] => [..., substring]
     *
     * <p><code>SUBSTRING [start:byte] [end:byte] [reverse:byte]</code>
     *
     * <p>Operands:
     * <ul>
     *   <li>start: Starting position (0-based index)</li>
     *   <li>end: Ending position (exclusive)</li>
     *   <li>reverse: If non-zero, count positions from the end of the string</li>
     * </ul>
     */
    public static final byte SUBSTRING = 34;

    /**
     * Pop a string and boolean from the stack and push whether it's a valid host label.
     *
     * <p>Stack: [..., string, allowDots] => [..., boolean]
     *
     * <p><code>IS_VALID_HOST_LABEL</code>
     */
    public static final byte IS_VALID_HOST_LABEL = 35;

    /**
     * Pop a string URL from the stack, parse it, and push the URI or null if invalid.
     *
     * <p>Stack: [..., urlString] => [..., uri|null]
     *
     * <p><code>PARSE_URL</code>
     */
    public static final byte PARSE_URL = 36;

    /**
     * Pop a string from the stack and push its URI-encoded form.
     *
     * <p>Stack: [..., string] => [..., encodedString]
     *
     * <p><code>URI_ENCODE</code>
     */
    public static final byte URI_ENCODE = 37;

    /**
     * Pop an error message from the stack and terminate with an error.
     *
     * <p>Stack: [..., errorMessage] => (terminates)
     *
     * <p><code>RETURN_ERROR</code>
     */
    public static final byte RETURN_ERROR = 38;

    /**
     * Build and return an endpoint. Pops URL, and optionally headers and properties based on flags.
     *
     * <p>Stack varies based on flags:
     * <ul>
     *   <li>No flags: [..., url] => (returns endpoint)</li>
     *   <li>Headers flag (bit 0): [..., headers, url] => (returns endpoint)</li>
     *   <li>Properties flag (bit 1): [..., properties, url] => (returns endpoint)</li>
     *   <li>Both flags: [..., properties, headers, url] => (returns endpoint)</li>
     * </ul>
     *
     * <p><code>RETURN_ENDPOINT [flags:byte]</code>
     *
     * <p>Flag bits:
     * <ul>
     *   <li>Bit 0 (0x01): Has headers</li>
     *   <li>Bit 1 (0x02): Has properties</li>
     * </ul>
     */
    public static final byte RETURN_ENDPOINT = 39;

    /**
     * Pop a value from the stack and return it as the result.
     *
     * <p>Stack: [..., value] => (returns value)
     *
     * <p><code>RETURN_VALUE</code>
     */
    public static final byte RETURN_VALUE = 40;

    /**
     * Jump forward if the value at the top of the stack is non-null.
     * If jumping, leave the value on the stack. If not jumping (null), pop the value.
     *
     * <p>This is used for null-coalescing operations where we want to short-circuit
     * on the first non-null value.
     *
     * <p>Stack: [..., value] => [..., value] (if non-null) or [...] (if null)
     *
     * <p><code>JNN_OR_POP [offset:ushort]</code>
     */
    public static final byte JNN_OR_POP = 42;
}
