/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

import software.amazon.smithy.model.node.Node;

sealed interface Instruction {

    Opcode opcode();

    void serialize(StringBuilder sink);

    record Push(Object value) implements Instruction {
        public Push {
            if (value instanceof Node) {
                throw new RuntimeException("?" + value);
            }
        }

        @Override
        public Opcode opcode() {
            return Opcode.PUSH;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",");
            EndpointUtils.serializeObject(value, sink);
        }
    }

    record PushRegister(int register) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.PUSH_REGISTER;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",").append(register);
        }
    }

    record PopRegister(int register) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.POP_REGISTER;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",").append(register);
        }
    }

    record LoadRegister(int register) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.LOAD_REGISTER;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",").append(register);
        }
    }

    final class Jump implements Instruction {
        private int target;

        Jump(int target) {
            this.target = target;
        }

        @Override
        public Opcode opcode() {
            return Opcode.JUMP;
        }

        void setTarget(int target) {
            this.target = target;
        }

        int target() {
            return target;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",").append(target);
        }
    }

    final class JumpIfFalsey implements Instruction {
        private int target;

        JumpIfFalsey(int target) {
            this.target = target;
        }

        @Override
        public Opcode opcode() {
            return Opcode.JUMP_IF_FALSEY;
        }

        void patchTarget(int target) {
            // Patch only if not already done.
            if (this.target == -1) {
                this.target = target;
            }
        }

        int target() {
            return target;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",").append(target);
        }
    }

    record Not() implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.NOT;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append('"');
        }
    }

    record Isset() implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.ISSET;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append('"');
        }
    }

    record SetError() implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.SET_ERROR;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append('"');
        }
    }

    record SetEndpoint(boolean hasHeaders, boolean hasProperties) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.SET_ENDPOINT;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"')
                    .append(opcode().toString())
                    .append("\",")
                    .append(hasHeaders)
                    .append(',')
                    .append(hasProperties);
        }
    }

    record CreateList(int listSize) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.CREATE_LIST;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",").append(listSize);
        }
    }

    record CreateMap(int mapSize) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.CREATE_MAP;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",").append(mapSize);
        }
    }

    record ResolveTemplate(StringTemplate template) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.RESOLVE_TEMPLATE;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",");
            EndpointUtils.serializeObject(template, sink);
        }
    }

    record Fn(int functionIndex) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.FN;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",").append(functionIndex);
        }
    }

    record GetAttr(AttrExpression expression) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.GET_ATTR;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",\"").append(expression).append('"');
        }
    }

    record IsTrue() implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.IS_TRUE;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append('"');
        }
    }

    record CompareRegister(int register, Object value) implements Instruction {
        @Override
        public Opcode opcode() {
            return Opcode.COMPARE_REGISTER;
        }

        @Override
        public void serialize(StringBuilder sink) {
            sink.append('"').append(opcode().toString()).append("\",").append(register).append(',');
            EndpointUtils.serializeObject(value, sink);
        }
    }
}
