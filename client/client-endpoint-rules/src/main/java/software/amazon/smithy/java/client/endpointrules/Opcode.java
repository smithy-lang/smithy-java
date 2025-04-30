/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

/**
 * Opcodes of the rules engine.
 */
enum Opcode {
    /**
     * Push a value onto the stack.
     *
     * @see Instruction.Push
     */
    PUSH,

    /**
     * Peeks the value at the top of the stack and pushes it onto the register stack of a register.
     *
     * @see Instruction.PushRegister
     */
    PUSH_REGISTER,

    /**
     * Pop a register value off its register stack.
     *
     * @see Instruction.PopRegister
     */
    POP_REGISTER,

    /**
     * Get the value of a register and push it onto the stack.
     *
     * @see Instruction.LoadRegister
     */
    LOAD_REGISTER,

    /**
     * Jumps to an opcode index.
     *
     * @see Instruction.Jump
     */
    JUMP,

    /**
     * Jumps to an opcode index if the top of the stack is null or false.
     *
     * @see Instruction.JumpIfFalsey
     */
    JUMP_IF_FALSEY,

    /**
     * Pops a value off the stack and pushes true if it is falsey (null or false), or false if not.
     *
     * <p>This implements the "not" function as an opcode.
     *
     * @see Instruction.Not
     */
    NOT,

    /**
     * Pops a value off the stack and pushes true if it is set (that is, not null).
     *
     * <p>This implements the "isset" function as an opcode.
     *
     * @see Instruction.Isset
     */
    ISSET,

    /**
     * Sets an error on the VM and exits.
     *
     * <p>Pops a single value that provides the error string to set.
     *
     * @see Instruction.SetError
     */
    SET_ERROR,

    /**
     * Sets the endpoint result of the VM and exits.
     *
     * <p>Pops two values:
     *
     * <ol>
     *     <li>The headers of the endpoint in the form of {@code Map<String, List<String>>}. Headers are resolved prior
     *     to SET_ENDPOINT using SET_ENDPOINT_HEADERS.</li>
     *     <li>The endpoint URL as a String that is parsed into a URI.</li>
     * </ol>
     *
     * @see Instruction.SetEndpoint
     */
    SET_ENDPOINT,

    /**
     * Pops N values off the stack and pushes a list of those values onto the stack.
     *
     * @see Instruction.CreateList
     */
    CREATE_LIST,

    /**
     * Pops N*2 values off the stack (key then value), creates a map of those values, and pushes the map onto the
     * stack. Each popped key must be a string.
     *
     * @see Instruction.CreateMap
     */
    CREATE_MAP,

    /**
     * Resolves a template string.
     *
     * <p>The corresponding instruction has a StringTemplate that tells the VM how many values to pop off the stack.
     * The popped values fill in values into the template. The resolved template value as a string is then pushed onto
     * the stack.
     *
     * @see Instruction.ResolveTemplate
     */
    RESOLVE_TEMPLATE,

    /**
     * Calls a function.
     *
     * <p>The function pops zero of more values off the stack based on the VmFunction registered for the index,
     * and then pushes the Object result onto the stack.
     *
     * @see Instruction.Fn
     */
    FN,

    /**
     * Pops the top level value and applies a getAttr expression on it, pushing the result onto the stack.
     *
     * @see Instruction.GetAttr
     */
    GET_ATTR,

    /**
     * Pops a value and pushes true if the value is boolean true, false if not.
     *
     * @see Instruction.IsTrue
     */
    IS_TRUE,

    /**
     * Compares a register with a value and pushes true if they are equal and false if they are not.
     *
     * @see Instruction.CompareRegister
     */
    COMPARE_REGISTER;
}
