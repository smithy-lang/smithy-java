/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NodeVisitor;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;

record NodeWriter(JavaWriter writer, Node node) implements NodeVisitor<Void>, Runnable {

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("node", Node.class);
        node.accept(this);
        writer.popState();
    }

    @Override
    public Void booleanNode(BooleanNode booleanNode) {
        writer.writeInline("${node:T}.from($L)", booleanNode.getValue());
        return null;
    }

    @Override
    public Void numberNode(NumberNode numberNode) {
        writer.writeInline("${node:T}.from($L)", numberNode.getValue());
        return null;
    }

    @Override
    public Void stringNode(StringNode stringNode) {
        writer.writeInline("${node:T}.from($S)", stringNode.getValue());
        return null;
    }

    @Override
    public Void objectNode(ObjectNode objectNode) {
        // Just use empty object node if no members
        if (objectNode.getMembers().isEmpty()) {
            writer.write("${node:T}.objectNode()");
            return null;
        }
        writer.write("${node:T}.objectNodeBuilder()");
        writer.indent();
        var memberWriter = new MemberNodeWriter(writer);
        for (var memberEntry : objectNode.getStringMap().entrySet()) {
            writer.write(
                ".withMember($S, $C)",
                memberEntry.getKey(),
                (Runnable) () -> memberEntry.getValue().accept(memberWriter)
            );
        }
        writer.writeWithNoFormatting(".build()");
        writer.dedent();
        return null;
    }

    @Override
    public Void arrayNode(ArrayNode arrayNode) {
        writer.write("$T.builder()", ArrayNode.class);
        writer.indent();
        var memberWriter = new MemberNodeWriter(writer);
        for (var element : arrayNode.getElements()) {
            writer.write(".withValue($C)", (Runnable) () -> element.accept(memberWriter));
        }
        writer.writeWithNoFormatting(".build()");
        writer.dedent();

        return null;
    }

    @Override
    public Void nullNode(NullNode nullNode) {
        writer.write("${node:T}.nullNode()");
        return null;
    }

    private record MemberNodeWriter(JavaWriter writer) implements NodeVisitor<Void> {

        @Override
        public Void booleanNode(BooleanNode booleanNode) {
            writer.writeInline("$L", booleanNode.getValue());
            return null;
        }

        @Override
        public Void nullNode(NullNode nullNode) {
            writer.writeInlineWithNoFormatting("null");
            return null;
        }

        @Override
        public Void numberNode(NumberNode numberNode) {
            writer.writeInline("$L", numberNode.getValue());
            return null;
        }

        @Override
        public Void objectNode(ObjectNode objectNode) {
            // Just use empty object node if no members
            if (objectNode.getMembers().isEmpty()) {
                writer.write("${node:T}.objectNode()");
                return null;
            }

            writer.write("${node:T}.objectNodeBuilder()");
            writer.indent();
            for (var memberEntry : objectNode.getStringMap().entrySet()) {
                writer.write(
                    ".withMember($S, $C)",
                    memberEntry.getKey(),
                    (Runnable) () -> memberEntry.getValue().accept(this)
                );
            }
            writer.writeWithNoFormatting(".build()");
            writer.dedent();
            return null;
        }

        @Override
        public Void arrayNode(ArrayNode arrayNode) {
            writer.write("$T.builder", ArrayNode.class);
            writer.indent();
            for (var element : arrayNode.getElements()) {
                writer.write(".withValue($C)", (Runnable) () -> element.accept(this));
            }
            writer.writeWithNoFormatting(".build()");
            writer.dedent();
            return null;
        }

        @Override
        public Void stringNode(StringNode stringNode) {
            writer.write("$S", stringNode.getValue());
            return null;
        }
    }
}
