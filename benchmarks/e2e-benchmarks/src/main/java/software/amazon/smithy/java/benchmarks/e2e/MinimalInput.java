/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.Locale;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.EnumTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 * Synthesizes the smallest input a response-side benchmark can send.
 *
 * <p>Only members the client refuses to send without are populated: URI labels and other {@code @required}
 * members. Everything else is legitimately optional and left unset, so the request side of a response benchmark
 * stays as small as the protocol allows. Values follow the protocol-test {@code params} conventions so they can be
 * fed through the same {@code ProtocolTestDocument} path as request cases.
 */
final class MinimalInput {

    private static final int MAX_DEPTH = 8;
    private static final long PLACEHOLDER_EPOCH_SECONDS = 1_700_000_000L;

    private MinimalInput() {}

    static ObjectNode forOperation(Model model, OperationShape operation) {
        var input = model.expectShape(operation.getInputShape(), StructureShape.class);
        return forStructure(model, input, 0);
    }

    private static ObjectNode forStructure(Model model, StructureShape structure, int depth) {
        var builder = Node.objectNodeBuilder();
        for (MemberShape member : structure.members()) {
            if (member.hasTrait(RequiredTrait.class) || member.hasTrait(HttpLabelTrait.class)) {
                builder.withMember(member.getMemberName(), placeholder(model, member, depth + 1));
            }
        }
        return builder.build();
    }

    @SuppressWarnings("deprecation") // EnumTrait is the fallback for legacy @enum strings.
    private static Node placeholder(Model model, MemberShape member, int depth) {
        if (depth > MAX_DEPTH) {
            return Node.objectNode();
        }
        Shape target = model.expectShape(member.getTarget());
        return switch (target.getType()) {
            case BLOB -> Node.from("");
            case BOOLEAN -> Node.from(true);
            case STRING -> target.getTrait(EnumTrait.class)
                    .map(trait -> Node.from(trait.getValues().get(0).getValue()))
                    .orElseGet(() -> Node.from("test-" + member.getMemberName().toLowerCase(Locale.ROOT)));
            case ENUM -> Node.from(target.asEnumShape().orElseThrow().getEnumValues().values().iterator().next());
            case INT_ENUM ->
                Node.from(target.asIntEnumShape().orElseThrow().getEnumValues().values().iterator().next());
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL -> Node.from(1);
            case TIMESTAMP -> Node.from(PLACEHOLDER_EPOCH_SECONDS);
            case DOCUMENT -> Node.objectNode();
            case LIST, SET -> Node.arrayNode();
            case MAP -> Node.objectNode();
            case STRUCTURE -> forStructure(model, target.asStructureShape().orElseThrow(), depth);
            case UNION -> {
                var first = target.asUnionShape().orElseThrow().members().iterator().next();
                yield Node.objectNodeBuilder()
                        .withMember(first.getMemberName(), placeholder(model, first, depth + 1))
                        .build();
            }
            default -> throw new IllegalArgumentException(
                    "Cannot synthesize a placeholder for " + member.getId() + " targeting " + target.getType());
        };
    }
}
